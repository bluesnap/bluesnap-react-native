export interface BSBaseSdkResult {
  billingDetails?: string | null;
  creditCard?: CreditCardProps;
  shippingDetails?: any;
  threeDSAuthenticationResult?: string;
  paymentInfo?: BSBaseSdkPaymentInfo;
}

export interface CreditCardProps {
  ccIssuingCountry: string;
  ccType: string;
  expirationMonth: string;
  expirationYear: string;
  last4Digits: string;
}

export interface BSBaseSdkPaymentInfo {
  currency: string;
  fraudSessionId: string;
  chosenPaymentMethodType: string;
  amount: number;
  taxAmount: number;
}
