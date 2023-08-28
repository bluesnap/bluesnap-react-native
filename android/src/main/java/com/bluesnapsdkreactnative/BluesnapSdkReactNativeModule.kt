package com.bluesnapsdkreactnative

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.View
import com.bluesnap.androidapi.http.BlueSnapHTTPResponse
import com.bluesnap.androidapi.models.BillingContactInfo
import com.bluesnap.androidapi.models.CreditCard
import com.bluesnap.androidapi.models.CreditCardInfo
import com.bluesnap.androidapi.models.PurchaseDetails
import com.bluesnap.androidapi.models.SdkRequest
import com.bluesnap.androidapi.models.SdkRequestBase
import com.bluesnap.androidapi.models.SdkRequestShopperRequirements
import com.bluesnap.androidapi.models.SdkResult
import com.bluesnap.androidapi.models.Shopper
import com.bluesnap.androidapi.models.SupportedPaymentMethods
import com.bluesnap.androidapi.services.BSPaymentRequestException
import com.bluesnap.androidapi.services.BlueSnapLocalBroadcastManager
import com.bluesnap.androidapi.services.BlueSnapService
import com.bluesnap.androidapi.services.BluesnapAlertDialog
import com.bluesnap.androidapi.services.BluesnapAlertDialog.BluesnapDialogCallback
import com.bluesnap.androidapi.services.BluesnapServiceCallback
import com.bluesnap.androidapi.services.CardinalManager
import com.bluesnap.androidapi.services.KountService
import com.bluesnap.androidapi.services.TaxCalculator
import com.bluesnap.androidapi.services.TokenProvider
import com.bluesnap.androidapi.services.TokenServiceCallback
import com.bluesnap.androidapi.views.activities.BluesnapCheckoutActivity
import com.bluesnap.androidapi.views.activities.BluesnapCheckoutActivity.REQUEST_CODE_DEFAULT
import com.bluesnapsdkreactnative.utils.AwaitLock
import com.bluesnapsdkreactnative.utils.DataConverter
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.BaseActivityEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.UiThreadUtil.runOnUiThread
import com.facebook.react.bridge.WritableMap
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection


@ReactModule(name = BluesnapSdkReactNativeModule.NAME)
class BluesnapSdkReactNativeModule(reactContext: ReactApplicationContext?) :
  ReactContextBaseJavaModule(reactContext) {

  companion object {
    var handlerThread: HandlerThread? = null
    var mainHandler: Handler? = null
    const val NAME = "BluesnapSdkReactNative"

    init {
      handlerThread = HandlerThread("MerchantTokenURLConnection")
      handlerThread!!.start()
      mainHandler = Handler(handlerThread!!.getLooper())

    }
  }

  private var bluesnapService: BlueSnapService? = null
  private var tokenProvider: TokenProvider? = null
  private var merchantToken: String? = null
  private var shopperId: Int? = null
  private var sdkRequest: SdkRequestBase? = null
  private var purchaseLock: AwaitLock? = null
  private var tokenGenerationLock: AwaitLock? = null
  private lateinit var awaitLockScope: CoroutineScope


  private val mActivityEventListener: ActivityEventListener =
    object : BaseActivityEventListener() {
      override fun onActivityResult(
        activity: Activity?,
        requestCode: Int,
        resultCode: Int,
        intent: Intent?
      ) {

        var isSubscription = false
        if (resultCode != BluesnapCheckoutActivity.RESULT_OK) {
          awaitLockScope.launch {
            val resultBundle: Bundle = Bundle();

            if (intent != null) {
              var sdkErrorMsg: String? = "SDK Failed to process the request: "
              sdkErrorMsg += intent.getStringExtra(BluesnapCheckoutActivity.SDK_ERROR_MSG)
              resultBundle.putString("errors", sdkErrorMsg)

              purchaseLock?.stopLock(resultBundle)
            } else {
              resultBundle.putString("errors", "Purchase canceled")
            }
          }

          return
        }

        // Here we can access the payment result
        val extras = intent?.extras
        val sdkResult =
          intent?.getParcelableExtra<SdkResult>(BluesnapCheckoutActivity.EXTRA_PAYMENT_RESULT)

        if (BluesnapCheckoutActivity.BS_CHECKOUT_RESULT_OK == sdkResult?.result) {
          //Handle checkout result
          awaitLockScope.launch {
            purchaseLock?.stopLock(extras);
          }

        }
        //Recreate the demo activity
        merchantToken = null
      }
    }

  init {
    this.purchaseLock = AwaitLock()
    this.tokenGenerationLock = AwaitLock()
    this.awaitLockScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    reactContext?.addActivityEventListener(mActivityEventListener)
    bluesnapService = BlueSnapService.getInstance()
  }

  override fun getName(): String {
    return NAME
  }

  /**
   * @deprecated
   */
//  @Deprecated
//  private fun merchantTokenService(tokenServiceInterface: TokenServiceInterface) {
//    val returningOrNewShopper = ""
//    val myRunnable = Runnable {
//      try {
//        val basicAuth = "Basic " +
//          Base64.encodeToString(
//            (DemoToken.SANDBOX_USER + ":" + DemoToken.SANDBOX_PASS)
//              .toByteArray(StandardCharsets.UTF_8), 0
//          )
//        val headerParams = ArrayList<CustomHTTPParams>()
//        headerParams.add(CustomHTTPParams("Authorization", basicAuth))
//        val post = HTTPOperationController.post(
//          DemoToken.SANDBOX_URL +
//            DemoToken.SANDBOX_TOKEN_CREATION +
//            returningOrNewShopper,
//          null,
//          "application/json",
//          "application/json",
//          headerParams
//        )
//        if (post.responseCode == HttpURLConnection.HTTP_CREATED && post.headers != null) {
//          val location = post.headers!!["Location"]!![0]
//          merchantToken = location.substring(location.lastIndexOf('/') + 1)
//          tokenServiceInterface.onServiceSuccess()
//        } else {
//          tokenServiceInterface.onServiceFailure()
//        }
//      } catch (e: Exception) {
//        tokenServiceInterface.onServiceFailure()
//      }
//    }
//    mainHandler?.post(myRunnable)
//  }

  private fun sendEvent(reactContext: ReactContext, eventName: String, params: WritableMap?) {
    reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit(eventName, params)
  }

  private var listenerCount = 0

  @ReactMethod
  fun addListener(eventName: String) {
    if (listenerCount == 0) {
      // Set up any upstream listeners or background tasks as necessary
    }

    listenerCount += 1
  }

  @ReactMethod
  fun removeListeners(count: Int) {
    listenerCount -= count
    if (listenerCount == 0) {
      // Remove upstream listeners, stop unnecessary background tasks
    }
  }

  /**
   * Called by the BlueSnapSDK when token expired error is recognized.
   * Here we ask React Native to generate a new token, so that when the action re-tries, it will succeedbdtren.
   */
  fun generateAndSetBsToken(completion: (token: String?, error: Any?) -> Unit) {
    Log.i(NAME, "generateAndSetBSToken, Got BS token expiration notification!")

    // Send an event to React Native, asking it to generate a token.
    var map = Arguments.createMap()
    if (shopperId != null) {
      map.putInt("shopperID", shopperId!!)
    } else {
      map.putNull("shopperID")
    }

    sendEvent(reactApplicationContext, "generateToken", map)

    awaitLockScope.launch {
      // Wait for the React Native token generator to call `finalizeToken`.
      tokenGenerationLock?.startLock(3000)
    }

    awaitLockScope.launch {
      // This will run after `finalizeToken` was called.
      val result = tokenGenerationLock?.awaitLock() as String?
      Log.i(NAME, "tokenGenerationLockker $result")
      merchantToken = result
      if (result.isNullOrEmpty()) {
        completion(null, "Unknown")
      } else {
        completion(result, null)
      }
    }
  }

  /**
   * This function shall only be called from React Native when the token generator finishes generating a token.
   */
  @ReactMethod
  fun finalizeToken(token: String?) {
    awaitLockScope.launch {
      if (token != null) {
        tokenGenerationLock?.stopLock(token)
      } else {
        tokenGenerationLock?.stopLock("")
      }
    }
  }

  /**
   *
   */
  @ReactMethod
  fun setSDKRequest(
    withEmail: Boolean,
    withShipping: Boolean,
    fullBilling: Boolean,
    amount: Double,
    taxAmount: Double,
    currency: String,
    activate3DS: Boolean
  ) {
    this.sdkRequest = SdkRequest(
      amount,
      currency,
      fullBilling,
      withEmail,
      withShipping
    )
    this.sdkRequest?.isActivate3DS = activate3DS

    //FIXME: Android not support set `taxAmount` in constructor for now
    this.sdkRequest!!.taxCalculator =
      TaxCalculator { shippingCountry, shippingState, priceDetails ->
        priceDetails.taxAmount = taxAmount
      }
  }

  /**
   *
   */
  @ReactMethod
  fun initBluesnap(
    bsToken: String?,
    initKount: Boolean,
    fraudSessionId: String?,
    applePayMerchantIdentifier: String?,
    merchantStoreCurrency: String?,
    promise: Promise
  ) {
    generateAndSetBsToken() { token, error ->
      Log.i(NAME, "generateAndSetBsToken $token $error")

      // create the interface for activating the token creation from server
      tokenProvider = TokenProvider { tokenServiceCallback ->
        if (error == null) {
          //change the expired token
          tokenServiceCallback.complete(token)
        } else { }
      }
      if (error == null) {
        //final String merchantStoreCurrency = (null == currency || null == currency.getCurrencyCode()) ? "USD" : currency.getCurrencyCode();
        currentActivity?.let {
          bluesnapService?.setup(
            token,
            tokenProvider,
            merchantStoreCurrency,
            it,
            object : BluesnapServiceCallback {
              override fun onSuccess() {
                runOnUiThread(Runnable {
                  promise.resolve("Success")

                })
              }

              override fun onFailure() {
                runOnUiThread(Runnable {
                  promise.reject("error_code", "Error description")
                })
              }
            })
        }
      } else {
        this@BluesnapSdkReactNativeModule.currentActivity?.let {
          BluesnapAlertDialog.setDialog(
            it,
            "Cannot obtain token from merchant server",
            "Service error",
            object : BluesnapDialogCallback {
              override fun setPositiveDialog() {}

              override fun setNegativeDialog() {
                initBluesnap(
                  bsToken,
                  initKount,
                  fraudSessionId,
                  applePayMerchantIdentifier,
                  merchantStoreCurrency,
                  promise
                )
              }
            },
            "Close",
            "Retry"
          )
        }
      }
    }
  }


  /**
   *
   */
  @ReactMethod
  fun showCheckout(
    promise: Promise
  ) {
    if (this.sdkRequest == null) {
      this.sdkRequest = SdkRequestShopperRequirements(
        true,
        true,
        true
      )
    }
//
//    if (activate3DS != null) {
//      this.sdkRequest?.isActivate3DS = activate3DS
//    }
    this.sdkRequest?.isGooglePayTestMode = true
    this.sdkRequest?.setGooglePayActive(true)

    try {
      this.sdkRequest!!.verify()
    } catch (e: BSPaymentRequestException) {
      Log.i(NAME, "showCheckout verify ${e.message}")
      Log.d(NAME, this.sdkRequest.toString())
      promise.reject("error_code", e.message)
    }

    // Set special tax policy: non-US pay no tax; MA pays 10%, other US states pay 5%
    this.sdkRequest!!.taxCalculator =
      TaxCalculator { shippingCountry, shippingState, priceDetails ->
        if ("us".equals(shippingCountry, ignoreCase = true)) {
          var taxRate = 0.05
          if ("ma".equals(shippingState, ignoreCase = true)) {
            taxRate = 0.1
          }
          priceDetails.taxAmount = priceDetails.subtotalAmount * taxRate
        } else {
          priceDetails.taxAmount = 0.0
        }
      }

    try {
      bluesnapService?.sdkRequest = this.sdkRequest!!
      Log.i(NAME, "showCheckout bluesnapService")

      awaitLockScope.launch {
        purchaseLock?.startLock(3000)
      }

      if (currentActivity == null) {
        promise.reject("error_code", "Activity null")
        return
      }

      val intent = Intent(currentActivity, BluesnapCheckoutActivity::class.java)
      currentActivity?.startActivityForResult(intent, REQUEST_CODE_DEFAULT)

      awaitLockScope.launch {
        val result = purchaseLock?.awaitLock();
        Log.i(NAME, "showCheckout result: ${result}")

        if (result != null) {
          promise.resolve(DataConverter.checkoutResultBundleToMap(result as Bundle))
        } else {
          promise.reject("error_code", "Error description")
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
      promise.reject("error_code", "payment request not validated")
    }
  }

  /**
   * tokenize Card On Server,
   * receive shopper and activate api tokenization to the server according to SDK Request [com.bluesnap.androidapi.models.SdkRequest] spec
   *
   * @param shopper      - [Shopper]
   * @param promise - [Promise]
   * @throws UnsupportedEncodingException - UnsupportedEncodingException
   * @throws JSONException                - JSONException
   */
  @Throws(UnsupportedEncodingException::class, JSONException::class)
  private fun tokenizeCardOnServer(shopper: Shopper, promise: Promise) {
    if (bluesnapService == null) {
      promise.reject("error_code", "bluesnapService is null")
      return;
    }

    val purchaseDetails = PurchaseDetails(
      shopper.newCreditCardInfo!!.creditCard,
      shopper.newCreditCardInfo!!.billingContactInfo,
      shopper.shippingContactInfo,
      shopper.isStoreCard
    )
    bluesnapService?.getAppExecutors()?.networkIO()?.execute(Runnable {
      try {
        val response: BlueSnapHTTPResponse? = bluesnapService?.submitTokenizedDetails(purchaseDetails)
        if (response?.responseCode == HttpURLConnection.HTTP_OK) {
          if (sdkRequest!!.isActivate3DS) {
            cardinal3DS(purchaseDetails, shopper, promise, response)
          } else {
            finishFromActivity(shopper, promise, response)
          }
        } else if (response?.responseCode == 400 &&
          null != bluesnapService?.getTokenProvider() && "" != response.responseString
        ) {
          try {
            val errorResponse = JSONObject(response.responseString)
            val rs2 = errorResponse["message"] as JSONArray
            val rs3 = rs2[0] as JSONObject
            if ("EXPIRED_TOKEN" == rs3["errorName"]) {
              bluesnapService?.getTokenProvider()?.getNewToken(TokenServiceCallback { newToken ->
                bluesnapService?.setNewToken(newToken)
                try {
                  tokenizeCardOnServer(shopper, promise)
                } catch (e: UnsupportedEncodingException) {
                  Log.e(NAME, "Unsupported Encoding Exception", e)
                  promise.reject("error_code", e.message)
                } catch (e: JSONException) {
                  Log.e(NAME, "json parsing exception")
                  promise.reject("error_code", e.message)
                }
              })
            } else {
              promise.reject("error_code", response.toString())
            }
          } catch (e: JSONException) {
            Log.e(NAME, "json parsing exception", e)
            promise.reject("error_code", e.message)
          }
        } else {
          promise.reject("error_code", response.toString())

        }
      } catch (ex: JSONException) {
        Log.e(NAME, "JsonException")
        promise.reject("error_code", ex.message)
      }
    })
  }

  private fun cardinal3DS(
    purchaseDetails: PurchaseDetails,
    shopper: Shopper,
    promise: Promise,
    response: BlueSnapHTTPResponse
  ) {

    // Request auth with 3DS
    val cardinalManager = CardinalManager.getInstance()
    val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        Log.d(NAME, "Got broadcastReceiver intent")
        if (cardinalManager.threeDSAuthResult == CardinalManager.ThreeDSManagerResponse.AUTHENTICATION_CANCELED.name) {
          Log.d(NAME, "Cardinal challenge canceled")
          //FIXME
//          progressBar.setVisibility(View.INVISIBLE)
//          runOnUiThread {
//            BluesnapAlertDialog.setDialog(
//              this@CreditCardActivity,
//              "3DS Authentication is required",
//              ""
//            )
//          }
        } else if (cardinalManager.threeDSAuthResult == CardinalManager.ThreeDSManagerResponse.AUTHENTICATION_FAILED.name || cardinalManager.threeDSAuthResult == CardinalManager.ThreeDSManagerResponse.THREE_DS_ERROR.name) { //cardinal internal error or authentication failure

          // TODO: Change this after receiving "proceed with/without 3DS" from server in init API call
          val error = intent.getStringExtra(CardinalManager.THREE_DS_AUTH_DONE_EVENT_NAME)
          promise.reject("error_code", error)
        } else { //cardinal success (success/bypass/unavailable/unsupported)
          Log.d(NAME, "3DS Flow ended properly")
          finishFromActivity(shopper, promise, response)
        }
      }
    }
    BlueSnapLocalBroadcastManager.registerReceiver(
      currentActivity,
      CardinalManager.THREE_DS_AUTH_DONE_EVENT,
      broadcastReceiver
    )
    try {
      cardinalManager?.authWith3DS(
        this.sdkRequest?.priceDetails?.currencyCode,
        this.sdkRequest?.priceDetails?.amount,
        currentActivity,
        purchaseDetails.creditCard
      )
    } catch (e: JSONException) {
      Log.d(NAME, "Error in parsing authWith3DS API response")
      promise.reject("error_code", "Error in parsing authWith3DS API response")
    }
  }

  /**
   * 3DS flow
   */
  private fun finishFromActivity(
    shopper: Shopper,
    promise: Promise,
    response: BlueSnapHTTPResponse
  ) {
    try {
      val Last4: String
      val ccType: String?
      val sdkResult = BlueSnapService.getInstance().sdkResult
      if (shopper.newCreditCardInfo!!.creditCard.isNewCreditCard) {
        // New Card
        val jsonObject = JSONObject(response.responseString)
        Last4 = jsonObject.getString("last4Digits")
        ccType = jsonObject.getString("ccType")
        Log.d(NAME, "tokenization of new credit card")
      } else {
        // Reused Card
        Last4 = shopper.newCreditCardInfo!!.creditCard.cardLastFourDigits
        ccType = shopper.newCreditCardInfo!!.creditCard.cardType
        Log.d(NAME, "tokenization of previous used credit card")
      }
      sdkResult.billingContactInfo = shopper.newCreditCardInfo!!.billingContactInfo
      if (sdkRequest!!.shopperCheckoutRequirements.isShippingRequired) sdkResult.shippingContactInfo =
        shopper.shippingContactInfo
      sdkResult.kountSessionId = KountService.getInstance().kountSessionId
      sdkResult.token = BlueSnapService.getInstance().blueSnapToken.merchantToken
      // update last4 from server result
      sdkResult.last4Digits = Last4
      // update card type from server result
      sdkResult.cardType = ccType
      sdkResult.chosenPaymentMethodType = SupportedPaymentMethods.CC
      sdkResult.threeDSAuthenticationResult = CardinalManager.getInstance().threeDSAuthResult
      val bundle = Bundle()
      bundle.putParcelable(BluesnapCheckoutActivity.EXTRA_PAYMENT_RESULT, sdkResult)
      bundle.putParcelable(BluesnapCheckoutActivity.EXTRA_BILLING_DETAILS,
        shopper.newCreditCardInfo?.billingContactInfo
      )
      //Only set the remember shopper here since failure can lead to missing tokenization on the server
      shopper.newCreditCardInfo!!.creditCard.setTokenizationSuccess()
      promise.resolve(DataConverter.checkoutResultBundleToMap(bundle))
      Log.d(NAME, "tokenization finished");
    } catch (e: NullPointerException) {
      promise.reject("error_code", e.message);
    } catch (e: JSONException) {
      promise.reject("error_code", e.message);
    }
  }

  /**
   *
   */
  @ReactMethod
  fun checkoutCard(
    props: ReadableMap,
    promise: Promise
  ) {
    val checkoutProps = DataConverter.toCheckoutCardProps(props)
    Log.i(NAME, "checkoutCard start for card ${checkoutProps.cardNumber}")
    val shopper: Shopper? = bluesnapService?.getsDKConfiguration()?.shopper;

    if (shopper != null) {
      //set credit card info
      val creditCard = CreditCard();
      creditCard.number = checkoutProps.cardNumber;
      creditCard.setExpDateFromString(checkoutProps.expirationDate);
      creditCard.cvc = checkoutProps.cvv;
      //set billing contact info
      val billingContactInfo = BillingContactInfo();
      billingContactInfo.fullName = checkoutProps.name;
      billingContactInfo.zip = checkoutProps.billingZip;
//      if (sdkRequest?.shopperCheckoutRequirements?.isEmailRequired == true) {
//        if (checkoutProps.email.isNullOrEmpty()) {
//          promise.reject("error_code", "Email is required");
//          return;
//        }
//      }
      billingContactInfo.email = checkoutProps.email;
      //FIXME: set address, city,...

      val creditCardInfo = CreditCardInfo();
      creditCardInfo.creditCard = creditCard;
      creditCardInfo.billingContactInfo = billingContactInfo;

      shopper.isStoreCard = checkoutProps.isStoreCard;
      shopper.newCreditCardInfo = creditCardInfo;

      val resultIntent = Intent()

      try {
        tokenizeCardOnServer(shopper, promise);
      } catch (e: Exception) {
        promise.reject("error_code", e.message);
      }
    } else {
      promise.reject("error_code", "Invalid shopper");
    }
  }

}
