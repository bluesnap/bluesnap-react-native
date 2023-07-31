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
                       currency: String
    ) -> Void {
        self.sdkre = BSSdkRequest(withEmail: withEmail, withShipping: withShipping, fullBilling: fullBilling, priceDetails: BSPriceDetails(amount: amount, taxAmount: taxAmount, currency: currency), billingDetails: nil, shippingDetails: nil, purchaseFunc: completePurchase, updateTaxFunc: updateTax)
        
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
    func showCheckout(_ activate3DS: Bool,
        resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) -> Void {
        NSLog("Show Checkout Screen")
        self.sdkre?.activate3DS = activate3DS
        Task { await self.purchaseLock.startLock(timeoutSeconds: 3000); }
        DispatchQueue.main.async {
            do {
                if let viewController = UIViewController.uiNavigationController, let sdk = self.sdkre {
                    try BlueSnapSDK.showCheckoutScreen(inNavigationController: viewController ,
                                                           animated: true,
                                                           sdkRequest: sdk)
                } else {
                    NSLog("Unable to get viewController")
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

