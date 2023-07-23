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
                  )

RCT_EXTERN_METHOD(showCheckout: (BOOL)activate3DS
                  resolve: (RCTPromiseResolveBlock)resolve
                  rejecter: (RCTPromiseRejectBlock)reject
                  )

RCT_EXTERN_METHOD(finalizeToken: (NSString *)token)

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
