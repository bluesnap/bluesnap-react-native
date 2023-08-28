package com.bluesnapsdkreactnative.models

class CheckoutCardProps(
  var cardNumber: String,
  var expirationDate: String,
  var cvv: String,
  var name: String,
  var billingZip: String,
  var isStoreCard: Boolean = false,
  var email: String?,
) {}
