import Foundation
import PassKit
import React
import UIKit
import BluesnapSDK

@available(iOS 13.0.0, *)
@objc(BluesnapSdkReactNative)
class BluesnapSdkReactNative: RCTEventEmitter {
    fileprivate var thisbsToken: BSToken?
    fileprivate var shouldInitKount = true
    final fileprivate var shopperId : Int? = nil
    final fileprivate var vaultedShopperId : String? = nil
    final fileprivate var threeDSResult : String? = nil
    var sdkre: BSSdkRequest?
    var navptr: UINavigationController?
    internal var currentPresenter: UIViewController?
    private var purchaseLock: AwaitLock
    
    private var tokenGenerationLock: AwaitLock
    
    override func supportedEvents() -> [String]! {
        return ["generateToken"]
    }
    
    public override init() {
        self.sdkre = nil
        self.purchaseLock = AwaitLock()
        self.tokenGenerationLock = AwaitLock()
        
        super.init()
    }
    
    /**
     Called by the BlueSnapSDK when token expired error is recognized.
     Here we ask React Native to generate a new token, so that when the action re-tries, it will succeed.
     */
    func generateAndSetBsToken(completion: @escaping (_ token: BSToken?, _ error: BSErrors?) -> Void) {
        NSLog("generateAndSetBSToken, Got BS token expiration notification!")
        
        // Send an event to React Native, asking it to generate a token.
        sendEvent(withName: "generateToken", body: ["shopperID": shopperId])
        
        Task {
            // Wait for the React Native token generator to call `finalizeToken`.
            await self.tokenGenerationLock.startLock(timeoutSeconds: 3000)
        }
        
        Task {
            // This will run after `finalizeToken` was called.
            if let result = await self.tokenGenerationLock.awaitLock() as? BSToken {
                completion(result, nil)
            } else {
                completion(nil, .unknown)
            }
        }
    }
    
    
    // This function shall only be called from React Native when the token generator finishes generating a token.
    @objc
    func finalizeToken(_ token: String?) -> Void {
        if let token = token, let bsToken = try? BSToken(tokenStr: token) {
            self.tokenGenerationLock.stopLock(withResult: bsToken)
        } else {
            self.tokenGenerationLock.stopLock(withResult: token as NSString?)
        }
    }
    
    private func completePurchase(purchaseDetails: BSBaseSdkResult!) {
        NSLog("ChosenPaymentMethodType: \(String(describing: purchaseDetails?.getCurrency()))")
        
        self.purchaseLock.stopLock(withResult: purchaseDetails);
    }
    
    func updateTax(_ shippingCountry: String,
                   _ shippingState: String?,
                   _ priceDetails: BSPriceDetails) -> Void {}
    
    @objc
    func setSDKRequest(_ withEmail: Bool,
                       withShipping: Bool,
                       fullBilling: Bool,
                       amount: Double,
                       taxAmount: Double,
                       currency: String,
                       activate3DS: Bool
    ) -> Void {
        self.sdkre = BSSdkRequest(withEmail: withEmail, withShipping: withShipping, fullBilling: fullBilling, priceDetails: BSPriceDetails(amount: amount, taxAmount: taxAmount, currency: currency), billingDetails: nil, shippingDetails: nil, purchaseFunc: completePurchase, updateTaxFunc: updateTax)
        
        self.sdkre?.activate3DS = activate3DS
    }
    
    @objc
    func initBluesnap(_ bsToken: String!,
                      initKount: Bool,
                      fraudSessionId: String?,
                      applePayMerchantIdentifier: String?,
                      merchantStoreCurrency: String?,
                      
                      resolver resolve: @escaping RCTPromiseResolveBlock,
                      rejecter reject: @escaping RCTPromiseRejectBlock) -> Void {
        // Your implementation here
        print("initBluesnap")
        
        NSLog("initBluesnap, start")
        let semaphore = DispatchSemaphore(value: 0)

        do {
            generateAndSetBsToken { resultToken, errors in
                self.thisbsToken = resultToken;
                NSLog("initBluesnap, generateAndSetBsToken")
                do {
                    try BlueSnapSDK.initBluesnap(
                        bsToken: self.thisbsToken,
                        generateTokenFunc: self.generateAndSetBsToken,
                        initKount: self.shouldInitKount,
                        fraudSessionId: nil,
                        applePayMerchantIdentifier: applePayMerchantIdentifier,
                        merchantStoreCurrency: merchantStoreCurrency,
                        completion: { error in
                            if let error = error {
                                NSLog("initBluesnap, error: \(error.description())")
                                reject("error_code", "Error description", NSError(domain: "", code: 200, userInfo: nil))
                                
                            } else {
                                NSLog("initBluesnap, Done")
                                
                                resolve("Success")
                            }
                        })
                    NSLog("initBluesnap, BlueSnapSDK initted blueSnap")
                } catch {
                    NSLog("initBluesnap, Unexpected error: \(error).")
                    reject("error_code", "Error description", NSError(domain: "", code: 200, userInfo: nil))
                }
            }
        }
        
        // If there was an error, call reject
        // For example:
        // reject("error_code", "Error description", NSError(domain: "", code: 200, userInfo: nil))
    }
    func getTopMostViewController() -> UIViewController? {
        if var topController = UIApplication.shared.keyWindow?.rootViewController {
            while let presentedViewController = topController.presentedViewController {
                topController = presentedViewController
            }
            return topController
        }
        return nil
    }


    @objc
    func showCheckout(_ resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) -> Void {
        NSLog("Show Checkout Screen")
        Task { await self.purchaseLock.startLock(timeoutSeconds: 3000); }
        DispatchQueue.main.async {
            do {
                NSLog("Show Checkout Screen1\(self.sdkre)")
                NSLog("Show Checkout Screen2\(UIViewController.uiNavigationController)")

                if let viewController = UIViewController.uiNavigationController, let sdk = self.sdkre {
                    try BlueSnapSDK.showCheckoutScreen(inNavigationController: viewController ,
                                                           animated: true,
                                                           sdkRequest: sdk)
                } else {
                    var errorMsg = "Unknown error"
                    if (UIViewController.uiNavigationController == nil) {
                        errorMsg = "Unable to get viewController"
                    } else if (self.sdkre == nil) {
                        errorMsg = "SDK not init properly"
                    }
                    NSLog(errorMsg)
                }
            } catch {
                NSLog("Unexpected error: \(error).")
            }
        }
        Task {
            if let result = await self.purchaseLock.awaitLock() as? BSBaseSdkResult {
                let dictionary = DataConverter.convertBSBaseSdkResultToNSDictionary(obj: result);
                resolve(dictionary);
            } else {
                reject(
                    "error_code",
                    "Error description",
                    NSError(domain: "", code: 200, userInfo: nil)
                );
            }
            
        }
    }

    func getCurrentYear() -> Int! {
        let date = Date()
        let calendar = Calendar(identifier: .gregorian)
        let year = calendar.component(.year, from: date)
        return year
    }
    
    public func getExpDateAsMMYYYY(value: String) -> String! {
        let newValue = value
        if let p = newValue.firstIndex(of: "/") {
            let mm = newValue[..<p]
            let yy = BSStringUtils.removeNoneDigits(String(newValue[p..<newValue.endIndex]))
            let currentYearStr = String(getCurrentYear())
            let p1 = currentYearStr.index(currentYearStr.startIndex, offsetBy: 2)
            let first2Digits = currentYearStr[..<p1]
            return "\(mm)/\(first2Digits)\(yy)"
        }
        return ""
    }
    
    /**
    *
    */
    public func submitPaymentFields(
        ccn: String,
        cvv: String,
        exp: String,
        purchaseDetails: BSCcSdkResult?,
        resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        BlueSnapSDK.sdkRequestBase = sdkre
        
        let formattedEXP = getExpDateAsMMYYYY(value: exp)
        
        BSApiManager.submitPurchaseDetails(
            ccNumber: ccn,
            expDate: formattedEXP,
            cvv: cvv,
            last4Digits: nil,
            cardType: nil, 
            billingDetails: purchaseDetails?.billingDetails,
            shippingDetails: purchaseDetails?.shippingDetails,
            storeCard: purchaseDetails?.storeCard,
            fraudSessionId: BlueSnapSDK.fraudSessionId,
            completion: { creditCard, error in

                //exp = getExpDateAsMMYYYY(value: exp)
                if let error = error {
                    if (error == .invalidCcNumber) {
                        reject(
                            "error_code",
                            BSValidator.ccnInvalidMessage,
                            NSError(domain: "", code: 200, userInfo: nil)
                        );
                    } else if (error == .invalidExpDate) {
                        reject(
                            "error_code",
                            BSValidator.expInvalidMessage,
                            NSError(domain: "", code: 200, userInfo: nil)
                        );
                    } else if (error == .invalidCvv) {
                        reject(
                            "error_code",
                            BSValidator.cvvInvalidMessage,
                            NSError(domain: "", code: 200, userInfo: nil)
                        );
                    } else if (error == .expiredToken) {
                        let message = "An error occurred. Please try again."
                        reject(
                            "error_code",
                            message,
                            NSError(domain: "", code: 200, userInfo: nil)
                        );
                    } else if (error == .tokenNotFound) {
                        let message = "An error occurred. Please try again."
                        reject(
                            "error_code",
                            message,
                            NSError(domain: "", code: 200, userInfo: nil)
                        );
                    } else {
                        NSLog("Unexpected error submitting Payment Fields to BS")
                        let message = "An error occurred. Please try again."
                        reject(
                            "error_code",
                            message,
                            NSError(domain: "", code: 200, userInfo: nil)
                        );
                    }
                }

                defer {
                    if let purchaseDetailsR = purchaseDetails {
                        if (BlueSnapSDK.sdkRequestBase?.activate3DS ?? false) {
                            // cardinalCompletion(ccn, creditCard, error)
                            BSCardinalManager.instance.authWith3DS(
                                currency: purchaseDetailsR.getCurrency(),
                                amount: String(purchaseDetailsR.getAmount()),
                                creditCardNumber: ccn,
                                    { cardinalResult, error2 in
                                                                        
                                        if (cardinalResult == ThreeDSManagerResponse.AUTHENTICATION_CANCELED.rawValue) { // cardinal challenge canceled
                                            NSLog(BSLocalizedStrings.getString(BSLocalizedString.Three_DS_Authentication_Required_Error))
                                            let message = BSLocalizedStrings.getString(BSLocalizedString.Three_DS_Authentication_Required_Error)
                                            reject(
                                                "error_code",
                                                message,
                                                NSError(domain: "", code: 200, userInfo: nil)
                                            );
                                            
                                        } else if (cardinalResult == ThreeDSManagerResponse.THREE_DS_ERROR.rawValue) { // server or cardinal internal error
                                            NSLog("Unexpected BS server error in 3DS authentication; error: \(error2)")
                                            let message = BSLocalizedStrings.getString(BSLocalizedString.Error_Three_DS_Authentication_Error) + "\n" + (error2?.description() ?? "")
                                            reject(
                                                "error_code",
                                                message,
                                                NSError(domain: "", code: 200, userInfo: nil)
                                            );
                                            
                                        } else if (cardinalResult == ThreeDSManagerResponse.AUTHENTICATION_FAILED.rawValue) { // authentication failure
                                            DispatchQueue.main.async {
                                                self.didSubmitCreditCard(purchaseDetails: purchaseDetails,creditCard: creditCard, error: error, resolve: resolve, rejecter: reject)
                                            }
                                            
                                        } else { // cardinal success (success/bypass/unavailable/unsupported)
                                            DispatchQueue.main.async {
                                                self.didSubmitCreditCard(
                                                    purchaseDetails: purchaseDetailsR,
                                                    creditCard: creditCard,
                                                    error: error,
                                                    resolve: resolve,
                                                    rejecter: reject
                                                )
                                            }
                                        }
                                        
                                    }
                            )
                            
                        } else {
                            DispatchQueue.main.async {
                                self.didSubmitCreditCard(
                                    purchaseDetails: purchaseDetailsR,
                                    creditCard: creditCard,
                                    error: error,
                                    resolve: resolve,
                                    rejecter: reject
                                )
                            }
                        }
                    }
                }
            }
        )
    }

    func didSubmitCreditCard(
        purchaseDetails: BSCcSdkResult?,
        creditCard: BSCreditCard,
        error: BSErrors?,
        resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        if let result = purchaseDetails {
            if let errorR = error {
                reject(
                    "error_code",
                    errorR.description(),
                    NSError(domain: "", code: 200, userInfo: nil)
                );
            } else {
                result.creditCard = creditCard
                result.threeDSAuthenticationResult = BSCardinalManager.instance.getThreeDSAuthResult()
                // execute callback
                BlueSnapSDK.sdkRequestBase?.purchaseFunc(result)
                
                let dictionary = DataConverter.convertBSBaseSdkResultToNSDictionary(obj: result);
                resolve(dictionary);
            }
            
        } else {
            reject(
                "error_code",
                "Payment result empty",
                NSError(domain: "", code: 200, userInfo: nil)
            );
        }
    }
    
    @objc
    func checkoutCard(_ props: NSDictionary,
        resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) -> Void {
        var checkoutProps = DataConverter.toCheckoutCardProps(dict: props);
        NSLog("checkoutCard start for card \(checkoutProps.cardNumber)")
       if let request = self.sdkre {
            request.shopperConfiguration.billingDetails?.name = checkoutProps.name;
            request.shopperConfiguration.billingDetails?.zip = checkoutProps.billingZip;
            request.shopperConfiguration.billingDetails?.email = checkoutProps.email;
           
           let purchaseDetails = BSCcSdkResult(sdkRequestBase: request)
           submitPaymentFields(
               ccn: checkoutProps.cardNumber,
               cvv: checkoutProps.cvv,
               exp: checkoutProps.expirationDate,
               purchaseDetails: purchaseDetails,
               resolve: resolve,
               rejecter: reject
           )
       } else {
           reject(
               "error_code",
               "Invalid request",
               NSError(domain: "", code: 200, userInfo: nil)
           )
       }
    }
}

extension UIView {
    var parentViewController: UIViewController? {
        var nextResponder: UIResponder? = self
        while nextResponder != nil {
            nextResponder = nextResponder?.next
            if let viewController = nextResponder as? UIViewController {
                return viewController
            }
        }
        return nil
    }
}

extension UIViewController {
    internal static var topPresenter: UIViewController? {
        var topController: UIViewController? = UIApplication.shared.keyWindow?.rootViewController
        
        while let presenter = topController?.presentedViewController {
            topController = presenter
        }
        return topController
    }
    
    internal static var uiNavigationController: UINavigationController? {
        let view = UIApplication.shared.delegate?.window?!.rootViewController
        if let viewController = view as? UINavigationController {
            return viewController
        }
        return nil
    }
}

