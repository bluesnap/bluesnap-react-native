package com.bluesnapsdkreactnative.utils

import android.os.Bundle
import com.bluesnap.androidapi.views.activities.BluesnapCheckoutActivity
import com.bluesnapsdkreactnative.models.CheckoutCardProps
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.google.gson.GsonBuilder

val GSON_MAPPER = GsonBuilder().serializeNulls().create()

class DataConverter {
  companion object {
    fun <T : Any> toMap(obj: T): ReadableMap {
      val ktMap = GSON_MAPPER.fromJson(GSON_MAPPER.toJson(obj), Map::class.java)
      val map = Arguments.createMap()

      for (key in ktMap.keys) {
        val data = ktMap[key]
        val keyStr = key.toString()

        when (data) {
          null -> map.putNull(keyStr)
          is Int -> map.putInt(keyStr, data)
          is Double -> map.putDouble(keyStr, data)
          is String -> map.putString(keyStr, data)
          is Boolean -> map.putBoolean(keyStr, data)
          is ReadableMap -> map.putMap(keyStr, data)
          is ReadableArray -> map.putArray(keyStr, data)
          else -> map.putMap(keyStr, toMap(data as Any))
        }
      }

      return map;
    }

    fun checkoutResultBundleToMap(obj: Bundle): ReadableMap {
      val map = Arguments.createMap()
      for (key in obj.keySet()) {
        if (key != null) {
          //FIXME: use bundle.get since we not know data type
          val data = obj.get(key);
          val keyStr = when (key) {
            BluesnapCheckoutActivity.EXTRA_BILLING_DETAILS -> "billingDetails"
            BluesnapCheckoutActivity.EXTRA_PAYMENT_RESULT -> "paymentInfo"
            else -> key.toString()
          }

          when (data) {
            null -> map.putNull(keyStr)
            is Int -> map.putInt(keyStr, data)
            is Double -> map.putDouble(keyStr, data)
            is String -> map.putString(keyStr, data)
            is Boolean -> map.putBoolean(keyStr, data)
            is ReadableMap -> map.putMap(keyStr, data)
            is ReadableArray -> map.putArray(keyStr, data)
            else -> map.putMap(keyStr, toMap(data as Any))
          }
        }
      }
      return map;
    }

    fun toCheckoutCardProps(map: ReadableMap): CheckoutCardProps {
      val cardNumber: String = when (map.hasKey("cardNumber")) {
        true -> map.getString("cardNumber").toString()
        else -> ""
      }
      val expirationDate = when (map.hasKey("expirationDate")) {
        true -> map.getString("expirationDate").toString()
        else -> ""
      }
      val cvv = when (map.hasKey("cvv")) {
        true -> map.getString("cvv").toString()
        else -> ""
      }
      val name = when (map.hasKey("name")) {
        true -> map.getString("name").toString()
        else -> ""
      }
      val billingZip = when (map.hasKey("billingZip")) {
        true -> map.getString("billingZip").toString()
        else -> ""
      }
      val isStoreCard = when (map.hasKey("isStoreCard")) {
        true -> map.getBoolean("isStoreCard")
        else -> false
      }
      val email = when (map.hasKey("email")) {
        true -> map.getString("email")
        else -> null
      }

      val props = CheckoutCardProps(cardNumber, expirationDate, cvv, name, billingZip, isStoreCard, email);

      return props;
    }
  }

}
