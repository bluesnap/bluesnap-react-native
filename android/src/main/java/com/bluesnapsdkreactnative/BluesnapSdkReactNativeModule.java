package com.bluesnapsdkreactnative;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bluesnap.androidapi.models.BillingContactInfo;
import com.bluesnap.androidapi.models.CreditCard;
import com.bluesnap.androidapi.models.PriceDetails;
import com.bluesnap.androidapi.models.PurchaseDetails;
import com.bluesnap.androidapi.models.SdkRequest;
import com.bluesnap.androidapi.models.SdkRequestShopperRequirements;
import com.bluesnap.androidapi.models.SdkRequestSubscriptionCharge;
import com.bluesnap.androidapi.models.SdkResult;
import com.bluesnap.androidapi.models.ShopperConfiguration;
import com.bluesnap.androidapi.services.BSPaymentRequestException;
import com.bluesnap.androidapi.services.BlueSnapService;
import com.bluesnap.androidapi.services.BluesnapServiceCallback;
import com.bluesnap.androidapi.services.CardinalManager;
import com.bluesnap.androidapi.services.TaxCalculator;
import com.bluesnap.androidapi.services.TokenProvider;
import com.bluesnap.androidapi.services.TokenServiceCallback;
import com.bluesnap.androidapi.views.activities.BluesnapCheckoutActivity;
import com.bluesnap.androidapi.views.activities.BluesnapChoosePaymentMethodActivity;
import com.bluesnap.androidapi.views.activities.BluesnapCreatePaymentActivity;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.Set;

@ReactModule(name = BluesnapSdkReactNativeModule.NAME)
public class BluesnapSdkReactNativeModule extends ReactContextBaseJavaModule
    implements ActivityEventListener {

  public static final String NAME = "BluesnapSdkReactNative";

  private static final int REQUEST_CHECKOUT = 1001;
  private static final int REQUEST_SUBSCRIPTION = 1002;
  private static final int REQUEST_CHOOSE_PAYMENT = 1003;
  private static final int REQUEST_CREATE_PAYMENT = 1004;

  private final ReactApplicationContext reactContext;
  private final BlueSnapService blueSnapService;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  @Nullable private Promise purchasePromise;
  @Nullable private TokenServiceCallback pendingTokenCallback;
  @Nullable private PriceDetails pendingTaxPriceDetails;

  public BluesnapSdkReactNativeModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
    this.blueSnapService = BlueSnapService.Companion.getInstance();
    reactContext.addActivityEventListener(this);
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

  @ReactMethod
  public void addListener(String eventName) {
    // Required for NativeEventEmitter
  }

  @ReactMethod
  public void removeListeners(double count) {
    // Required for NativeEventEmitter
  }

  @ReactMethod
  public void initBluesnap(ReadableMap options, Promise promise) {
    if (!options.hasKey("token") || options.isNull("token")) {
      promise.reject("INVALID_TOKEN", "token is required");
      return;
    }

    final String token = options.getString("token");
    final String merchantStoreCurrency =
        options.hasKey("merchantStoreCurrency") && !options.isNull("merchantStoreCurrency")
            ? options.getString("merchantStoreCurrency")
            : "USD";

    TokenProvider tokenProvider = new TokenProvider() {
      @Override
      public void getNewToken(final TokenServiceCallback tokenServiceCallback) {
        pendingTokenCallback = tokenServiceCallback;
        sendEvent("onRequestNewToken", null);
      }
    };

    blueSnapService.setup(
        token,
        tokenProvider,
        merchantStoreCurrency,
        reactContext.getApplicationContext(),
        new BluesnapServiceCallback() {
          @Override
          public void onSuccess() {
            mainHandler.post(() -> promise.resolve(null));
          }

          @Override
          public void onFailure() {
            mainHandler.post(() -> promise.reject("INIT_FAILED", "BlueSnap SDK setup failed"));
          }
        }
    );
  }

  @ReactMethod
  public void setBsToken(String token, Promise promise) {
    try {
      blueSnapService.setNewToken(token);
      if (pendingTokenCallback != null) {
        pendingTokenCallback.complete(token);
        pendingTokenCallback = null;
      }
      promise.resolve(null);
    } catch (Exception e) {
      if (pendingTokenCallback != null) {
        pendingTokenCallback.complete(null);
        pendingTokenCallback = null;
      }
      promise.reject("SET_TOKEN_FAILED", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void showCheckout(ReadableMap request, Promise promise) {
    startCheckoutFlow(request, false, REQUEST_CHECKOUT, promise);
  }

  @ReactMethod
  public void showSubscriptionCheckout(ReadableMap request, Promise promise) {
    startSubscriptionFlow(request, promise);
  }

  @ReactMethod
  public void showChoosePayment(ReadableMap request, Promise promise) {
    Activity activity = getCurrentActivity();
    if (activity == null) {
      promise.reject("NO_ACTIVITY", "No active activity");
      return;
    }

    purchasePromise = promise;
    SdkRequestShopperRequirements sdkRequest = new SdkRequestShopperRequirements(
        getBoolean(request, "billingRequired", false),
        getBoolean(request, "emailRequired", false),
        getBoolean(request, "shippingRequired", false)
    );
    sdkRequest.setGooglePayActive(getBoolean(request, "googlePayActive", true));

    try {
      blueSnapService.setSdkRequest(sdkRequest);
      Intent intent = new Intent(activity, BluesnapChoosePaymentMethodActivity.class);
      activity.startActivityForResult(intent, REQUEST_CHOOSE_PAYMENT);
    } catch (BSPaymentRequestException e) {
      clearPurchasePromise("CHECKOUT_FAILED", e.getMessage());
    }
  }

  @ReactMethod
  public void showCreatePayment(ReadableMap request, Promise promise) {
    startCheckoutFlow(request, false, REQUEST_CREATE_PAYMENT, promise);
  }

  @ReactMethod
  public void submitTokenizedDetails(ReadableMap request, Promise promise) {
    ReadableMap card = request.getMap("card");
    if (card == null) {
      promise.reject("INVALID_REQUEST", "card is required");
      return;
    }

    CreditCard creditCard = new CreditCard();
    creditCard.setNumber(card.getString("cardNumber"));
    creditCard.setCvv(card.getString("cvv"));
    creditCard.setExpDateFromString(
        card.getString("expirationMonth") + "/" + card.getString("expirationYear")
    );

    BillingContactInfo billing = SdkResultMapper.billingFromMap(request.getMap("billing"));
    boolean storeCard = getBoolean(request, "storeCard", false);

    PurchaseDetails purchaseDetails;
    if (request.hasKey("shipping") && !request.isNull("shipping")) {
      purchaseDetails = new PurchaseDetails(
          creditCard,
          billing,
          SdkResultMapper.shippingFromMap(request.getMap("shipping")),
          storeCard
      );
    } else {
      purchaseDetails = new PurchaseDetails(creditCard, billing, storeCard);
    }

    try {
      blueSnapService.submitTokenizedDetails(purchaseDetails);
      promise.resolve(Arguments.createMap());
    } catch (Exception e) {
      promise.reject("TOKENIZE_FAILED", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void authenticate3DS(ReadableMap request, Promise promise) {
    Activity activity = getCurrentActivity();
    if (activity == null) {
      promise.reject("NO_ACTIVITY", "No active activity");
      return;
    }

    String currency = request.hasKey("currency") ? request.getString("currency") : "USD";
    String amountStr = request.hasKey("amount") ? request.getString("amount") : "0";
    double amount;
    try {
      amount = Double.parseDouble(amountStr);
    } catch (NumberFormatException e) {
      promise.reject("INVALID_AMOUNT", "amount must be a numeric string");
      return;
    }

    CreditCard creditCard = new CreditCard();
    if (request.hasKey("creditCardNumber") && !request.isNull("creditCardNumber")) {
      creditCard.setNumber(request.getString("creditCardNumber"));
    }

    try {
      CardinalManager.getInstance().authWith3DS(currency, amount, activity, creditCard);
      SdkResult sdkResult = blueSnapService.getSdkResult();
      String threeDsResult =
          sdkResult != null ? sdkResult.getThreeDSAuthenticationResult() : null;
      if (threeDsResult != null) {
        promise.resolve(threeDsResult);
      } else {
        promise.resolve(CardinalManager.getInstance().getThreeDSAuthResult());
      }
    } catch (Exception e) {
      promise.reject("THREE_DS_FAILED", e.getMessage(), e);
    }
  }

  @ReactMethod
  public void respondToTaxUpdate(double taxAmount) {
    if (pendingTaxPriceDetails != null) {
      pendingTaxPriceDetails.setTaxAmount(taxAmount);
      pendingTaxPriceDetails = null;
    }
  }

  @ReactMethod
  public void getSupportedCurrencies(Promise promise) {
    Set<String> rates = blueSnapService.getSupportedRates();
    WritableArray array = Arguments.createArray();
    if (rates != null) {
      for (String rate : rates) {
        array.pushString(rate);
      }
    }
    promise.resolve(array);
  }

  @ReactMethod
  public void applePaySupported(Promise promise) {
  WritableMap map = Arguments.createMap();
    map.putBoolean("canMakePayments", false);
    map.putBoolean("canSetupCards", false);
    promise.resolve(map);
  }

  @ReactMethod
  public void getCards(Promise promise) {
    promise.resolve(Arguments.createArray());
  }

  @ReactMethod
  public void getShopperConfiguration(Promise promise) {
    ShopperConfiguration shopper = blueSnapService.getShopperConfiguration();
    if (shopper == null) {
      promise.resolve(null);
      return;
    }
    promise.resolve(SdkResultMapper.shopperConfigurationToMap(shopper));
  }

  @ReactMethod
  public void getSdkVersion(Promise promise) {
    promise.resolve("2.5.1");
  }

  @Override
  public void onActivityResult(
      Activity activity,
      int requestCode,
      int resultCode,
      @Nullable Intent data
  ) {
    if (purchasePromise == null) {
      return;
    }

    boolean handledRequest =
        requestCode == REQUEST_CHECKOUT
            || requestCode == REQUEST_SUBSCRIPTION
            || requestCode == REQUEST_CHOOSE_PAYMENT
            || requestCode == REQUEST_CREATE_PAYMENT;

    if (!handledRequest) {
      return;
    }

    if (resultCode != Activity.RESULT_OK || data == null) {
      String errorMessage = "Checkout was cancelled";
      if (data != null && data.hasExtra(BluesnapCheckoutActivity.SDK_ERROR_MSG)) {
        errorMessage = data.getStringExtra(BluesnapCheckoutActivity.SDK_ERROR_MSG);
      }
      clearPurchasePromise("PURCHASE_CANCELLED", errorMessage);
      return;
    }

    SdkResult sdkResult = data.getParcelableExtra(BluesnapCheckoutActivity.EXTRA_PAYMENT_RESULT);
    if (sdkResult == null) {
      clearPurchasePromise("PURCHASE_FAILED", "Missing payment result");
      return;
    }

    Promise promise = purchasePromise;
    purchasePromise = null;
    promise.resolve(SdkResultMapper.toWritableMap(sdkResult));
  }

  @Override
  public void onNewIntent(Intent intent) {}

  private void startCheckoutFlow(
      ReadableMap request,
      boolean subscription,
      int requestCode,
      Promise promise
  ) {
    Activity activity = getCurrentActivity();
    if (activity == null) {
      promise.reject("NO_ACTIVITY", "No active activity");
      return;
    }

    purchasePromise = promise;

    double amount = request.hasKey("amount") ? request.getDouble("amount") : 0;
    String currency = request.hasKey("currency") ? request.getString("currency") : "USD";
    boolean billingRequired = getBoolean(request, "billingRequired", false);
    boolean emailRequired = getBoolean(request, "emailRequired", false);
    boolean shippingRequired = getBoolean(request, "shippingRequired", false);

    SdkRequest sdkRequest = new SdkRequest(
        amount,
        currency,
        billingRequired,
        emailRequired,
        shippingRequired
    );

    applyCommonRequestOptions(sdkRequest, request);

    if (shippingRequired) {
      sdkRequest.setTaxCalculator(new TaxCalculator() {
        @Override
        public void updateTax(
            String shippingCountry,
            String shippingState,
            PriceDetails priceDetails
        ) {
          pendingTaxPriceDetails = priceDetails;
          WritableMap event = Arguments.createMap();
          event.putString("country", shippingCountry);
          event.putString("state", shippingState);
          event.putDouble("amount", priceDetails.getSubtotalAmount());
          event.putDouble("taxAmount", priceDetails.getTaxAmount());
          event.putString("currency", priceDetails.getCurrencyCode());
          sendEvent("onTaxUpdate", event);
        }
      });
    }

    Class<?> activityClass =
        requestCode == REQUEST_CREATE_PAYMENT
            ? BluesnapCreatePaymentActivity.class
            : BluesnapCheckoutActivity.class;

    try {
      sdkRequest.verify();
      blueSnapService.setSdkRequest(sdkRequest);
      Intent intent = new Intent(activity, activityClass);
      activity.startActivityForResult(intent, requestCode);
    } catch (BSPaymentRequestException e) {
      clearPurchasePromise("CHECKOUT_FAILED", e.getMessage());
    }
  }

  private void startSubscriptionFlow(ReadableMap request, Promise promise) {
    Activity activity = getCurrentActivity();
    if (activity == null) {
      promise.reject("NO_ACTIVITY", "No active activity");
      return;
    }

    purchasePromise = promise;

    boolean billingRequired = getBoolean(request, "billingRequired", false);
    boolean emailRequired = getBoolean(request, "emailRequired", false);
    boolean shippingRequired = getBoolean(request, "shippingRequired", false);

    SdkRequestSubscriptionCharge sdkRequest;
    if (!request.hasKey("amount") || request.isNull("amount")) {
      sdkRequest = new SdkRequestSubscriptionCharge(billingRequired, emailRequired, shippingRequired);
    } else {
      double amount = request.getDouble("amount");
      String currency = request.hasKey("currency") ? request.getString("currency") : "USD";
      sdkRequest = new SdkRequestSubscriptionCharge(
          amount,
          currency,
          billingRequired,
          emailRequired,
          shippingRequired
      );
    }

    if (getBoolean(request, "showSubscriptionCancellationMessage", false)) {
      sdkRequest.setShowSubscriptionCancellationMessage(true);
    }
    if (request.hasKey("subscriptionCancellationMessage")
        && !request.isNull("subscriptionCancellationMessage")) {
      sdkRequest.setCustomSubscriptionCancellationMessage(
          request.getString("subscriptionCancellationMessage")
      );
    }

    applyCommonRequestOptions(sdkRequest, request);

    try {
      sdkRequest.verify();
      blueSnapService.setSdkRequest(sdkRequest);
      Intent intent = new Intent(activity, BluesnapCheckoutActivity.class);
      activity.startActivityForResult(intent, REQUEST_SUBSCRIPTION);
    } catch (BSPaymentRequestException e) {
      clearPurchasePromise("CHECKOUT_FAILED", e.getMessage());
    }
  }

  private void applyCommonRequestOptions(
      com.bluesnap.androidapi.models.SdkRequestBase sdkRequest,
      ReadableMap request
  ) {
    sdkRequest.setAllowCurrencyChange(getBoolean(request, "allowCurrencyChange", true));
    sdkRequest.setHideStoreCardSwitch(getBoolean(request, "hideStoreCardSwitch", false));
    sdkRequest.setActivate3DS(getBoolean(request, "activate3DS", false));
    sdkRequest.setGooglePayActive(getBoolean(request, "googlePayActive", true));
    sdkRequest.setGooglePayTestMode(getBoolean(request, "googlePayTestMode", false));
  }

  private boolean getBoolean(ReadableMap map, String key, boolean defaultValue) {
    return map.hasKey(key) && !map.isNull(key) ? map.getBoolean(key) : defaultValue;
  }

  private void clearPurchasePromise(String code, String message) {
    if (purchasePromise != null) {
      purchasePromise.reject(code, message);
      purchasePromise = null;
    }
  }

  private void sendEvent(String eventName, @Nullable WritableMap params) {
    if (reactContext.hasActiveCatalystInstance()) {
      reactContext
          .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
          .emit(eventName, params);
    }
  }
}
