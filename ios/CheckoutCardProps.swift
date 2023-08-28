//
//  CheckoutCardProps.swift
//  BluesnapSdkReactNative
//
//  Created by MacOS on 8/18/23.
//  Copyright © 2023 Facebook. All rights reserved.
//

import Foundation

class CheckoutCardProps {
    var cardNumber: String
    var expirationDate: String
    var cvv: String
    var name: String
    var billingZip: String
    var isStoreCard: Bool
    var email: String?

    init () {
        self.cardNumber = ""
        self.expirationDate = ""
        self.cvv = ""
        self.name = ""
        self.billingZip = ""
        self.isStoreCard = false
        self.email = nil
    }

}