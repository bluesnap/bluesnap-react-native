#import <React/RCTBridgeModule.h>
#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

@interface RCT_EXTERN_MODULE(BluesnapSdkReactNative, NSObject)

RCT_EXTERN_METHOD(setSDKRequest: (BOOL)withEmail
                  withShipping: (BOOL)withShipping
                  fullBilling: (BOOL)fullBilling
                  amount: (double)amount
                  taxAmount: (double)taxAmount
                  currency: (NSString *)currency
                  activate3DS: (BOOL)activate3DS
                )

RCT_EXTERN_METHOD(showCheckout: (RCTPromiseResolveBlock)resolve
                  rejecter: (RCTPromiseRejectBlock)reject
                )

RCT_EXTERN_METHOD(finalizeToken: (NSString *)token)

RCT_EXTERN_METHOD(checkoutCard: (NSDictionary)props
                  resolve: (RCTPromiseResolveBlock)resolve
                  rejecter: (RCTPromiseRejectBlock)reject
                )

+ (BOOL)requiresMainQueueSetup
{
  return YES;
}

RCT_EXTERN_METHOD(initBluesnap: (NSString *)bsToken
                  initKount: (BOOL)initKount
                  fraudSessionId: (NSString *)fraudSessionId
                  applePayMerchantIdentifier: (NSString *)applePayMerchantIdentifier
                  merchantStoreCurrency: (NSString *)merchantStoreCurrency
                  resolver: (RCTPromiseResolveBlock)resolve
                  rejecter: (RCTPromiseRejectBlock)reject
                )

@end
