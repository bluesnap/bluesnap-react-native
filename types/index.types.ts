export interface BSBaseSdkResult {
  creditCard?: CreditCardProps;
  shippingDetails?: any;
  threeDSAuthenticationResult?: string;
  paymentInfo?: BSBaseSdkPaymentInfo;
  billingDetails?: BSBaseSdkBillingDetails | null;
}

export interface CreditCardProps {
  ccIssuingCountry?: string;
  ccType?: string;
  expirationMonth?: string;
  expirationYear?: string;
  last4Digits?: string;
}

export interface BSBaseSdkPaymentInfo {
  currency?: string;
  fraudSessionId?: string;
  chosenPaymentMethodType?: string;
  amount?: number;
  taxAmount?: number;

  //From Android
  billingContactInfo?: BSBaseSdkBillingDetails | null;
  cardType?: string;
  currencyNameCode?: string;
  expDate?: null;
  googlePayToken?: null;
  kountSessionId?: string;
  last4Digits?: string;
  paypalInvoiceId?: null;
  result?: number;
  shippingContactInfo?: null;
  threeDSAuthenticationResult?: string;
  token?: string;
}

export interface BSBaseSdkBillingDetails {
  address?: string;
  address2?: null;
  city?: string;
  country?: string;
  email?: null;
  firstName?: string;
  lastName?: string;
  state?: string;
  zip?: string;
}
