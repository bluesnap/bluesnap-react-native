import Foundation
import UIKit
import PassKit
import BluesnapSDK

@objc(BluesnapSdkReactNative)
class BluesnapSdkReactNative: RCTEventEmitter {

  private var purchaseResolve: RCTPromiseResolveBlock?
  private var purchaseReject: RCTPromiseRejectBlock?
  private var tokenRefreshCompletion: ((BSToken?, BSErrors?) -> Void)?
  private var taxUpdateHandler: ((String, String?, BSPriceDetails) -> Void)?
  private var presentedNavigationController: UINavigationController?

  override static func requiresMainQueueSetup() -> Bool {
    return true
  }

  override func supportedEvents() -> [String]! {
    return ["onRequestNewToken", "onTaxUpdate"]
  }

  // MARK: - Initialization

  @objc(initBluesnap:withResolver:withRejecter:)
  func initBluesnap(
    _ options: NSDictionary,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
  DispatchQueue.main.async {
      guard let tokenStr = options["token"] as? String else {
        reject("INVALID_TOKEN", "token is required", nil)
        return
      }

      let initKount = options["initKount"] as? Bool ?? true
      let fraudSessionId = options["fraudSessionId"] as? String
      let applePayMerchantId = options["applePayMerchantIdentifier"] as? String
      let merchantStoreCurrency = options["merchantStoreCurrency"] as? String

      do {
        let bsToken = try BSToken(tokenStr: tokenStr)
        try BlueSnapSDK.initBluesnap(
          bsToken: bsToken,
          generateTokenFunc: { [weak self] completion in
            self?.tokenRefreshCompletion = completion
            self?.sendEvent(withName: "onRequestNewToken", body: nil)
          },
          initKount: initKount,
          fraudSessionId: fraudSessionId,
          applePayMerchantIdentifier: applePayMerchantId,
          merchantStoreCurrency: merchantStoreCurrency
        ) { error in
          if let error = error {
            reject("INIT_FAILED", error.localizedDescription, nil)
          } else {
            resolve(nil)
          }
        }
      } catch {
        reject("INIT_FAILED", error.localizedDescription, error)
      }
    }
  }

  @objc(setBsToken:withResolver:withRejecter:)
  func setBsToken(
    _ token: String,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    DispatchQueue.main.async {
      do {
        let bsToken = try BSToken(tokenStr: token)
        try BlueSnapSDK.setBsToken(bsToken: bsToken)

        if let completion = self.tokenRefreshCompletion {
          self.tokenRefreshCompletion = nil
          completion(bsToken, nil)
        }
        resolve(nil)
      } catch {
        if let completion = self.tokenRefreshCompletion {
          self.tokenRefreshCompletion = nil
          completion(nil, BSErrors.expiredToken)
        }
        reject("SET_TOKEN_FAILED", error.localizedDescription, error)
      }
    }
  }

  // MARK: - Checkout flows

  @objc(showCheckout:withResolver:withRejecter:)
  func showCheckout(
    _ request: NSDictionary,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    presentFlow(request: request, flow: .checkout, resolve: resolve, reject: reject)
  }

  @objc(showSubscriptionCheckout:withResolver:withRejecter:)
  func showSubscriptionCheckout(
    _ request: NSDictionary,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    presentFlow(request: request, flow: .subscription, resolve: resolve, reject: reject)
  }

  @objc(showChoosePayment:withResolver:withRejecter:)
  func showChoosePayment(
    _ request: NSDictionary,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    presentFlow(request: request, flow: .choosePayment, resolve: resolve, reject: reject)
  }

  @objc(showCreatePayment:withResolver:withRejecter:)
  func showCreatePayment(
    _ request: NSDictionary,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    presentFlow(request: request, flow: .createPayment, resolve: resolve, reject: reject)
  }

  private enum CheckoutFlow {
    case checkout, subscription, choosePayment, createPayment
  }

  private func presentFlow(
    request: NSDictionary,
    flow: CheckoutFlow,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    DispatchQueue.main.async {
      self.purchaseResolve = resolve
      self.purchaseReject = reject

      guard let navController = self.resolveNavigationController() else {
        reject("NO_NAVIGATION", "Unable to find a navigation controller to present checkout", nil)
        self.clearPurchaseCallbacks()
        return
      }

      do {
        let sdkRequestBase = try self.buildSdkRequest(from: request, flow: flow)

        switch flow {
        case .checkout, .subscription:
          try BlueSnapSDK.showCheckoutScreen(
            inNavigationController: navController,
            animated: true,
            sdkRequest: sdkRequestBase as! BSSdkRequest
          )
        case .choosePayment:
          try BlueSnapSDK.showChoosePaymentScreen(
            inNavigationController: navController,
            animated: true,
            sdkRequestShopperRequirements: sdkRequestBase as! BSSdkRequestShopperRequirements
          )
        case .createPayment:
          try BlueSnapSDK.showCreatePaymentScreen(
            inNavigationController: navController,
            animated: true,
            sdkRequest: sdkRequestBase as! BSSdkRequest
          )
        }
      } catch {
        reject("CHECKOUT_FAILED", error.localizedDescription, error)
        self.clearPurchaseCallbacks()
      }
    }
  }

  private func buildSdkRequest(from request: NSDictionary, flow: CheckoutFlow) throws -> BSSdkRequestProtocol {
    let emailRequired = request["emailRequired"] as? Bool ?? false
    let shippingRequired = request["shippingRequired"] as? Bool ?? false
    let billingRequired = request["billingRequired"] as? Bool ?? false
    let billingDetails = RnSdkMapper.billingDetails(from: request["billingDetails"] as? NSDictionary)
    let shippingDetails = RnSdkMapper.shippingDetails(from: request["shippingDetails"] as? NSDictionary)

    let purchaseFunc: (BSBaseSdkResult?) -> Void = { [weak self] result in
      guard let self = self else { return }
      DispatchQueue.main.async {
        if let result = result {
          self.purchaseResolve?(RnSdkMapper.purchaseResult(from: result))
        } else {
          self.purchaseReject?("PURCHASE_CANCELLED", "Checkout was cancelled", nil)
        }
        self.clearPurchaseCallbacks()
        self.dismissPresentedNavigationIfNeeded()
      }
    }

    let updateTaxFunc: ((String, String?, BSPriceDetails) -> Void)? = shippingRequired
      ? { [weak self] country, state, priceDetails in
          self?.taxUpdateHandler = { _, _, details in
            details.taxAmount = NSNumber(value: details.taxAmount.doubleValue)
          }
          self?.sendEvent(
            withName: "onTaxUpdate",
            body: [
              "country": country,
              "state": state as Any,
              "amount": priceDetails.amount.doubleValue,
              "taxAmount": priceDetails.taxAmount.doubleValue,
              "currency": priceDetails.currency ?? "USD",
            ]
          )
          // Store priceDetails reference for respondToTaxUpdate
          self?.pendingTaxPriceDetails = priceDetails
        }
      : nil

    switch flow {
    case .checkout, .createPayment:
      let amount = request["amount"] as? Double ?? 0
      let taxAmount = request["taxAmount"] as? Double ?? 0
      let currency = request["currency"] as? String ?? "USD"
      let priceDetails = BSPriceDetails(amount: amount, taxAmount: taxAmount, currency: currency)

      let sdkRequest = BSSdkRequest(
        withEmail: emailRequired,
        withShipping: shippingRequired,
        fullBilling: billingRequired,
        priceDetails: priceDetails,
        billingDetails: billingDetails,
        shippingDetails: shippingDetails,
        purchaseFunc: purchaseFunc,
        updateTaxFunc: updateTaxFunc
      )
      sdkRequest.allowCurrencyChange = request["allowCurrencyChange"] as? Bool ?? true
      sdkRequest.hideStoreCardSwitch = request["hideStoreCardSwitch"] as? Bool ?? false
      sdkRequest.activate3DS = request["activate3DS"] as? Bool ?? false
      return sdkRequest

    case .subscription:
      let showMessage = request["showSubscriptionCancellationMessage"] as? Bool ?? false
      let message = request["subscriptionCancellationMessage"] as? String

      if let amount = request["amount"] as? Double {
        let taxAmount = request["taxAmount"] as? Double ?? 0
        let currency = request["currency"] as? String ?? "USD"
        let priceDetails = BSPriceDetails(amount: amount, taxAmount: taxAmount, currency: currency)
        let sdkRequest = BSSdkRequestSubscriptionCharge(
          withEmail: emailRequired,
          withShipping: shippingRequired,
          fullBilling: billingRequired,
          priceDetails: priceDetails,
          billingDetails: billingDetails,
          shippingDetails: shippingDetails,
          purchaseFunc: purchaseFunc,
          updateTaxFunc: updateTaxFunc,
          subscriptionCancellationMessage: message,
          showSubscriptionCancellationMessage: showMessage
        )
        sdkRequest.allowCurrencyChange = request["allowCurrencyChange"] as? Bool ?? true
        sdkRequest.hideStoreCardSwitch = request["hideStoreCardSwitch"] as? Bool ?? false
        sdkRequest.activate3DS = request["activate3DS"] as? Bool ?? false
        return sdkRequest
      }

      let sdkRequest = BSSdkRequestSubscriptionCharge(
        withEmail: emailRequired,
        withShipping: shippingRequired,
        fullBilling: billingRequired,
        billingDetails: billingDetails,
        shippingDetails: shippingDetails,
        purchaseFunc: purchaseFunc,
        subscriptionCancellationMessage: message,
        showSubscriptionCancellationMessage: showMessage
      )
      sdkRequest.allowCurrencyChange = request["allowCurrencyChange"] as? Bool ?? true
      sdkRequest.hideStoreCardSwitch = request["hideStoreCardSwitch"] as? Bool ?? false
      sdkRequest.activate3DS = request["activate3DS"] as? Bool ?? false
      return sdkRequest

    case .choosePayment:
      return BSSdkRequestShopperRequirements(
        withEmail: emailRequired,
        withShipping: shippingRequired,
        fullBilling: billingRequired,
        billingDetails: billingDetails,
        shippingDetails: shippingDetails,
        purchaseFunc: purchaseFunc
      )
    }
  }

  private var pendingTaxPriceDetails: BSPriceDetails?

  @objc(respondToTaxUpdate:)
  func respondToTaxUpdate(_ taxAmount: Double) {
    pendingTaxPriceDetails?.taxAmount = NSNumber(value: taxAmount)
    pendingTaxPriceDetails = nil
  }

  // MARK: - Custom UI

  @objc(submitTokenizedDetails:withResolver:withRejecter:)
  func submitTokenizedDetails(
    _ request: NSDictionary,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    guard let card = request["card"] as? NSDictionary else {
      reject("INVALID_REQUEST", "card is required", nil)
      return
    }

    let ccDetails = BSTokenizeNewCCDetails(
      ccNumber: card["cardNumber"] as? String,
      cvv: card["cvv"] as? String,
      ccType: "",
      expDate: RnSdkMapper.expirationDate(
        month: card["expirationMonth"] as? String,
        year: card["expirationYear"] as? String
      )
    )

    let tokenizeRequest = BSTokenizeRequest()
    tokenizeRequest.paymentDetails = ccDetails
    tokenizeRequest.billingDetails = RnSdkMapper.billingDetails(from: request["billing"] as? NSDictionary)
    tokenizeRequest.shippingDetails = RnSdkMapper.shippingDetails(from: request["shipping"] as? NSDictionary)
    tokenizeRequest.storeCard = request["storeCard"] as? Bool ?? false

    BlueSnapSDK.submitTokenizedDetails(tokenizeRequest: tokenizeRequest) { result, error in
      if let error = error {
        reject("TOKENIZE_FAILED", error.localizedDescription, nil)
      } else {
        resolve(result ?? [:])
      }
    }
  }

  @objc(authenticate3DS:withResolver:withRejecter:)
  func authenticate3DS(
    _ request: NSDictionary,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    let currency = request["currency"] as? String ?? "USD"
    let amount = request["amount"] as? String ?? "0"
    let cardNumber = request["creditCardNumber"] as? String

    BlueSnapSDK.authenticationWith3DS(
      currency: currency,
      amount: amount,
      creditCardNumber: cardNumber
    ) { result, error in
      if let error = error {
        reject("THREE_DS_FAILED", error.localizedDescription, nil)
      } else {
        resolve(result)
      }
    }
  }

  // MARK: - Utilities

  @objc(getSupportedCurrencies:withRejecter:)
  func getSupportedCurrencies(
    resolve: RCTPromiseResolveBlock,
    reject: RCTPromiseRejectBlock
  ) {
    resolve(RnSdkMapper.supportedCurrencyCodes(from: BlueSnapSDK.getCurrencyRates()))
  }

  @objc(applePaySupported:withRejecter:)
  func applePaySupported(
    resolve: RCTPromiseResolveBlock,
    reject: RCTPromiseRejectBlock
  ) {
    let networks: [PKPaymentNetwork] = [.amex, .discover, .masterCard, .visa]
    let support = BlueSnapSDK.applePaySupported(
      supportedPaymentMethods: nil,
      supportedNetworks: networks
    )
    resolve([
      "canMakePayments": support.canMakePayments,
      "canSetupCards": support.canSetupCards,
    ])
  }

  @objc(getCards:withRejecter:)
  func getCards(
    resolve: RCTPromiseResolveBlock,
    reject: RCTPromiseRejectBlock
  ) {
    let cards = BlueSnapSDK.getCards()
    resolve(RnSdkMapper.creditCards(from: cards))
  }

  @objc(getShopperConfiguration:withRejecter:)
  func getShopperConfiguration(
    resolve: RCTPromiseResolveBlock,
    reject: RCTPromiseRejectBlock
  ) {
    resolve(NSNull())
  }

  @objc(getSdkVersion:withRejecter:)
  func getSdkVersion(
    resolve: RCTPromiseResolveBlock,
    reject: RCTPromiseRejectBlock
  ) {
    resolve("2.2.0")
  }

  // MARK: - Navigation helpers

  private func resolveNavigationController() -> UINavigationController? {
    guard let rootVC = RnNavigationHelper.topViewController() else { return nil }

    if let nav = rootVC.navigationController {
      return nav
    }

    let nav = UINavigationController(rootViewController: UIViewController())
    nav.modalPresentationStyle = .fullScreen
    nav.isNavigationBarHidden = true
    rootVC.present(nav, animated: true)
    presentedNavigationController = nav
    return nav
  }

  private func dismissPresentedNavigationIfNeeded() {
    presentedNavigationController?.dismiss(animated: true)
    presentedNavigationController = nil
  }

  private func clearPurchaseCallbacks() {
    purchaseResolve = nil
    purchaseReject = nil
  }
}

// MARK: - Mapper

private enum RnSdkMapper {
  static let commonCurrencyCodes = [
    "USD", "EUR", "GBP", "CAD", "AUD", "ILS", "JPY", "CHF", "SEK", "NOK", "DKK", "NZD", "SGD", "HKD",
  ]

  static func billingDetails(from dict: NSDictionary?) -> BSBillingAddressDetails? {
    guard let dict = dict else { return nil }
    return BSBillingAddressDetails(
      email: dict["email"] as? String,
      name: dict["name"] as? String,
      address: dict["address"] as? String,
      city: dict["city"] as? String,
      zip: dict["zip"] as? String,
      country: dict["country"] as? String,
      state: dict["state"] as? String
    )
  }

  static func shippingDetails(from dict: NSDictionary?) -> BSShippingAddressDetails? {
    guard let dict = dict else { return nil }
    return BSShippingAddressDetails(
      name: dict["name"] as? String,
      address: dict["address"] as? String,
      city: dict["city"] as? String,
      zip: dict["zip"] as? String,
      country: dict["country"] as? String,
      state: dict["state"] as? String
    )
  }

  static func supportedCurrencyCodes(from currencies: BSCurrencies?) -> [String] {
    guard let currencies = currencies else { return [] }
    return commonCurrencyCodes.filter { currencies.getCurrencyByCode(code: $0) != nil }
  }

  static func expirationDate(month: String?, year: String?) -> String {
    guard let month = month, let year = year else { return "" }
    return "\(month)/\(year)"
  }

  static func purchaseResult(from result: BSBaseSdkResult) -> [String: Any] {
    var map: [String: Any] = [
      "fraudSessionId": result.getFraudSessionId() as Any,
      "isShopperRequirements": result.isShopperRequirements(),
      "isSubscriptionCharge": result.isSubscriptionCharge(),
    ]

    if result.hasPriceDetails() {
      map["amount"] = result.getAmount() as Any
      map["taxAmount"] = result.getTaxAmount() as Any
      map["currency"] = result.getCurrency() as Any
    }

    if let paymentType = result.getChosenPaymentMethodType() {
      map["paymentType"] = paymentType.rawValue
    }

    if let ccResult = result as? BSCcSdkResult {
      map["last4Digits"] = ccResult.creditCard.last4Digits as Any
      map["cardType"] = ccResult.creditCard.ccType as Any
      map["issuingCountry"] = ccResult.creditCard.ccIssuingCountry as Any
      map["threeDSAuthenticationResult"] = ccResult.threeDSAuthenticationResult as Any
      map["storeCard"] = ccResult.storeCard as Any
      if let billing = ccResult.getBillingDetails() {
        map["billingDetails"] = addressMap(from: billing)
      }
      if let shipping = ccResult.getShippingDetails() {
        map["shippingDetails"] = shippingMap(from: shipping)
      }
    }

    if let paypalResult = result as? BSPayPalSdkResult {
      map["payPalInvoiceId"] = paypalResult.payPalInvoiceId as Any
    }

    return map
  }

  static func addressMap(from billing: BSBillingAddressDetails) -> [String: Any?] {
    [
      "email": billing.email,
      "name": billing.name,
      "address": billing.address,
      "city": billing.city,
      "zip": billing.zip,
      "country": billing.country,
      "state": billing.state,
    ]
  }

  static func shippingMap(from shipping: BSShippingAddressDetails) -> [String: Any?] {
    [
      "name": shipping.name,
      "address": shipping.address,
      "city": shipping.city,
      "zip": shipping.zip,
      "country": shipping.country,
      "state": shipping.state,
    ]
  }

  static func creditCards(from cards: [BSCreditCard]?) -> [[String: Any?]] {
    guard let cards = cards else { return [] }
    return cards.map { card in
      [
        "last4Digits": card.last4Digits,
        "cardType": card.ccType,
        "expirationMonth": card.expirationMonth,
        "expirationYear": card.expirationYear,
        "issuingCountry": card.ccIssuingCountry,
      ]
    }
  }
}

// MARK: - Navigation helper

private enum RnNavigationHelper {
  static func topViewController(
    base: UIViewController? = UIApplication.shared.connectedScenes
      .compactMap { ($0 as? UIWindowScene)?.keyWindow }
      .first?.rootViewController
  ) -> UIViewController? {
    if let nav = base as? UINavigationController {
      return topViewController(base: nav.visibleViewController)
    }
    if let tab = base as? UITabBarController, let selected = tab.selectedViewController {
      return topViewController(base: selected)
    }
    if let presented = base?.presentedViewController {
      return topViewController(base: presented)
    }
    return base
  }
}
