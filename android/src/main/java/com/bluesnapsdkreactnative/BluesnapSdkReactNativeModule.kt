package com.bluesnapsdkreactnative

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import com.bluesnap.androidapi.http.CustomHTTPParams
import com.bluesnap.androidapi.http.HTTPOperationController
import com.bluesnap.androidapi.models.SdkRequest
import com.bluesnap.androidapi.models.SdkRequestBase
import com.bluesnap.androidapi.models.SdkRequestShopperRequirements
import com.bluesnap.androidapi.models.SdkResult
import com.bluesnap.androidapi.services.BSPaymentRequestException
import com.bluesnap.androidapi.services.BlueSnapService
import com.bluesnap.androidapi.services.BluesnapAlertDialog
import com.bluesnap.androidapi.services.BluesnapAlertDialog.BluesnapDialogCallback
import com.bluesnap.androidapi.services.BluesnapServiceCallback
import com.bluesnap.androidapi.services.TaxCalculator
import com.bluesnap.androidapi.services.TokenProvider
import com.bluesnap.androidapi.views.activities.BluesnapCheckoutActivity
import com.bluesnap.androidapi.views.activities.BluesnapCheckoutActivity.REQUEST_CODE_DEFAULT
import com.bluesnapsdkreactnative.services.TokenServiceInterface
import com.bluesnapsdkreactnative.utils.AwaitLock
import com.bluesnapsdkreactnative.utils.DataConverter
import com.bluesnapsdkreactnative.utils.DemoToken
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.BaseActivityEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.UiThreadUtil.runOnUiThread
import com.facebook.react.bridge.WritableMap
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.Deprecated
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets

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
  Called by the BlueSnapSDK when token expired error is recognized.
  Here we ask React Native to generate a new token, so that when the action re-tries, it will succeed.
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
    currency: String
  ) {
    this.sdkRequest = SdkRequest(
      amount,
      currency,
      fullBilling,
      withEmail,
      withShipping
    )

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
                  promise.resolve("initBluesnap success")

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
    activate3DS: Boolean?,
    promise: Promise
  ) {
    if (this.sdkRequest == null) {
      this.sdkRequest = SdkRequestShopperRequirements(
        true,
        true,
        true
      )
    }

    if (activate3DS != null) {
      this.sdkRequest?.isActivate3DS = activate3DS
    }
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
}
