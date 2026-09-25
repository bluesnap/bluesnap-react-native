package com.bluesnapsdkreactnative;

import androidx.annotation.Nullable;

import com.bluesnap.androidapi.models.BillingContactInfo;
import com.bluesnap.androidapi.models.SdkResult;
import com.bluesnap.androidapi.models.ShippingContactInfo;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

public final class SdkResultMapper {

  private SdkResultMapper() {}

  public static WritableMap toWritableMap(SdkResult result) {
    WritableMap map = Arguments.createMap();
    if (result == null) {
      return map;
    }

    if (result.getAmount() != null) {
      map.putDouble("amount", result.getAmount());
    }
    if (result.getCurrencyNameCode() != null) {
      map.putString("currency", result.getCurrencyNameCode());
    }
    putString(map, "paymentType", result.getChosenPaymentMethodType());
    putString(map, "token", result.getToken());
    putString(map, "last4Digits", result.getLast4Digits());
    putString(map, "cardType", result.getCardType());
    putString(map, "expDate", result.getExpDate());
    putString(map, "payPalInvoiceId", result.getPaypalInvoiceId());
    putString(map, "googlePayToken", result.getGooglePayToken());
    putString(map, "kountSessionId", result.getKountSessionId());
    putString(map, "threeDSAuthenticationResult", result.getThreeDSAuthenticationResult());
    putString(map, "paymentMethod", result.getPaymentMethod());
    putString(map, "routingNumber", result.getRoutingNumber());
    putString(map, "accountNumber", result.getAccountNumber());
    putString(map, "accountType", result.getAccountType());

    if (result.getBillingContactInfo() != null) {
      map.putMap("billingDetails", contactToMap(result.getBillingContactInfo(), true));
    }
    if (result.getShippingContactInfo() != null) {
      map.putMap("shippingDetails", contactToMap(result.getShippingContactInfo(), false));
    }

    return map;
  }

  public static BillingContactInfo billingFromMap(@Nullable ReadableMap map) {
    if (map == null) {
      return null;
    }
    BillingContactInfo billing = new BillingContactInfo();
    billing.setEmail(getString(map, "email"));
    billing.setFullName(getString(map, "name"));
    billing.setAddress(getString(map, "address"));
    billing.setCity(getString(map, "city"));
    billing.setZip(getString(map, "zip"));
    billing.setCountry(getString(map, "country"));
    billing.setState(getString(map, "state"));
    return billing;
  }

  public static ShippingContactInfo shippingFromMap(@Nullable ReadableMap map) {
    if (map == null) {
      return null;
    }
    ShippingContactInfo shipping = new ShippingContactInfo();
    shipping.setFullName(getString(map, "name"));
    shipping.setAddress(getString(map, "address"));
    shipping.setCity(getString(map, "city"));
    shipping.setZip(getString(map, "zip"));
    shipping.setCountry(getString(map, "country"));
    shipping.setState(getString(map, "state"));
    return shipping;
  }

  public static WritableMap shopperConfigurationToMap(
      com.bluesnap.androidapi.models.ShopperConfiguration shopper
  ) {
    WritableMap map = Arguments.createMap();
    if (shopper == null) {
      return map;
    }

    if (shopper.getBillingContactInfo() != null) {
      map.putMap("billingDetails", contactToMap(shopper.getBillingContactInfo(), true));
    }
    if (shopper.getShippingContactInfo() != null) {
      map.putMap("shippingDetails", contactToMap(shopper.getShippingContactInfo(), false));
    }

    if (shopper.getChosenPaymentMethod() != null) {
      map.putString(
          "chosenPaymentMethodType",
          shopper.getChosenPaymentMethod().getChosenPaymentMethodType()
      );
      if (shopper.getChosenPaymentMethod().getCreditCard() != null) {
        WritableMap card = Arguments.createMap();
        card.putString(
            "last4Digits",
            shopper.getChosenPaymentMethod().getCreditCard().getCardLastFourDigits()
        );
        card.putString("cardType", shopper.getChosenPaymentMethod().getCreditCard().getCardType());
        map.putMap("creditCard", card);
      }
    }

    return map;
  }

  private static WritableMap contactToMap(
      com.bluesnap.androidapi.models.ContactInfo contact,
      boolean includeEmail
  ) {
    WritableMap map = Arguments.createMap();
    if (contact == null) {
      return map;
    }
    if (includeEmail && contact instanceof BillingContactInfo) {
      putString(map, "email", ((BillingContactInfo) contact).getEmail());
    }
    putString(map, "name", contact.getFullName());
    putString(map, "address", contact.getAddress());
    putString(map, "city", contact.getCity());
    putString(map, "zip", contact.getZip());
    putString(map, "country", contact.getCountry());
    putString(map, "state", contact.getState());
    return map;
  }

  private static void putString(WritableMap map, String key, @Nullable String value) {
    if (value != null) {
      map.putString(key, value);
    }
  }

  private static String getString(ReadableMap map, String key) {
    return map.hasKey(key) && !map.isNull(key) ? map.getString(key) : null;
  }
}
