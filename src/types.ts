export type PaymentType = 'CC' | 'APPLE_PAY' | 'PAYPAL' | 'ECP' | 'ACH';

export interface InitOptions {
  /** Payment-fields token from POST /services/2/payment-fields-tokens */
  token: string;
  /** Initialize Kount fraud detection (recommended) */
  initKount?: boolean;
  /** Optional fraud session ID (max 32 chars); generated if omitted */
  fraudSessionId?: string;
  /** Apple Pay merchant identifier (iOS only) */
  applePayMerchantIdentifier?: string;
  /** Merchant base currency for rate calculations (ISO 4217) */
  merchantStoreCurrency?: string;
}

export interface AddressDetails {
  email?: string;
  name?: string;
  address?: string;
  city?: string;
  zip?: string;
  country?: string;
  state?: string;
}

export interface CheckoutRequest {
  amount: number;
  currency: string;
  taxAmount?: number;
  billingRequired?: boolean;
  emailRequired?: boolean;
  shippingRequired?: boolean;
  allowCurrencyChange?: boolean;
  hideStoreCardSwitch?: boolean;
  activate3DS?: boolean;
  /** Android only */
  googlePayActive?: boolean;
  /** Android only */
  googlePayTestMode?: boolean;
  billingDetails?: AddressDetails;
  shippingDetails?: AddressDetails;
}

export interface SubscriptionCheckoutRequest {
  amount?: number;
  currency?: string;
  taxAmount?: number;
  billingRequired?: boolean;
  emailRequired?: boolean;
  shippingRequired?: boolean;
  allowCurrencyChange?: boolean;
  hideStoreCardSwitch?: boolean;
  activate3DS?: boolean;
  googlePayActive?: boolean;
  googlePayTestMode?: boolean;
  billingDetails?: AddressDetails;
  shippingDetails?: AddressDetails;
  showSubscriptionCancellationMessage?: boolean;
  subscriptionCancellationMessage?: string;
}

export interface ShopperRequirementsRequest {
  billingRequired?: boolean;
  emailRequired?: boolean;
  shippingRequired?: boolean;
  /** Android only */
  googlePayActive?: boolean;
  billingDetails?: AddressDetails;
  shippingDetails?: AddressDetails;
}

export interface CreditCardDetails {
  cardNumber: string;
  cvv: string;
  expirationMonth: string;
  expirationYear: string;
}

export interface TokenizeCardRequest {
  card: CreditCardDetails;
  billing?: AddressDetails;
  shipping?: AddressDetails;
  storeCard?: boolean;
}

export interface ThreeDSRequest {
  currency: string;
  amount: string;
  creditCardNumber?: string;
}

export interface TaxUpdateEvent {
  country: string;
  state?: string;
  amount: number;
  taxAmount: number;
  currency: string;
}

export interface PurchaseResult {
  paymentType?: PaymentType;
  amount?: number;
  taxAmount?: number;
  currency?: string;
  fraudSessionId?: string;
  storeCard?: boolean;
  token?: string;
  last4Digits?: string;
  cardType?: string;
  expDate?: string;
  issuingCountry?: string;
  threeDSAuthenticationResult?: string;
  payPalInvoiceId?: string;
  googlePayToken?: string;
  kountSessionId?: string;
  billingDetails?: AddressDetails;
  shippingDetails?: AddressDetails;
  isShopperRequirements?: boolean;
  isSubscriptionCharge?: boolean;
}

export interface ApplePaySupport {
  canMakePayments: boolean;
  canSetupCards: boolean;
}

export interface CreditCardInfo {
  last4Digits?: string;
  cardType?: string;
  expirationMonth?: string;
  expirationYear?: string;
  issuingCountry?: string;
}

export interface ShopperConfiguration {
  billingDetails?: AddressDetails;
  shippingDetails?: AddressDetails;
  chosenPaymentMethodType?: PaymentType;
  creditCard?: CreditCardInfo;
}

export type BluesnapEventMap = {
  onRequestNewToken: void;
  onTaxUpdate: TaxUpdateEvent;
};
