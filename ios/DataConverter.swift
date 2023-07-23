//
//  DataConverter.swift
//  BluesnapSdkReactNative
//
//  Created by MacOS on 7/13/23.
//  Copyright © 2023 Facebook. All rights reserved.
//

import Foundation
import PassKit
import BluesnapSDK

class DataConverter {
    static func toNSDictionary(obj: Any) -> [String: Any] {
        var dict: [String: Any] = [:];
        
        let mirror = Mirror(reflecting: obj)
        for child in mirror.children {
            guard let key = child.label else { continue }
            let childMirror = Mirror(reflecting: child.value)
            
            switch childMirror.displayStyle {
            case .struct, .class:
                let childDict = toNSDictionary(obj: child.value)
                dict[key] = childDict
            case .collection:
                let childArray = (child.value as! any Collection).map({ toNSDictionary(obj: $0) })
                dict[key] = childArray
            case .set:
                let childArray = (child.value as! Set<AnyHashable>).map({ toNSDictionary(obj: $0) })
                dict[key] = childArray
            default:
                dict[key] = child.value
            }
        }
        
        return dict;
    }
    
    
    static func convertBSBaseSdkResultToNSDictionary(obj: BSBaseSdkResult) -> [String: Any] {
        var dict: [String: Any] = self.toNSDictionary(obj: obj);
        
        dict["paymentInfo"] = [
            "currency": obj.getCurrency() ?? "",
            "fraudSessionId": obj.getFraudSessionId() ?? "",
            "chosenPaymentMethodType": obj.getChosenPaymentMethodType() ?? "",
            "amount": obj.getAmount() ?? 0,
            "taxAmount": obj.getTaxAmount() ?? 0
        ];
        
        return dict;
    }
}
