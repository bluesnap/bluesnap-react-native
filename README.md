# bluesnap-sdk-react-native

A BlueSnap SDK React Native Bridge

## Installation


###   checkout 
Add dependency to the checkout directory in your package.json
```sh
    "bluesnap-sdk-react-native": "file:../bluesnap-sdk-react-native/",
```

### npm (in progress)
```sh
npm install bluesnap-sdk-react-native 
```

## Token
The Bluesnap Native SDKs require a short lived token in order to call API. You should obtain the token during runtime from your backend and pass it to the intialization method.

## Example applications 
This project contains example React Native application that demonstrates the SDK 

### Example application - iOS 

1. Check out the repostory and run 'yarn ios'
2. Open the example app's `src` directory, add a new file called `credentials.json`. In the file, create an object that contains 2 strings, one called `username` and the other called `password`, each of them must be set to their respective values:

```
{
    "username": "REPLACE_ME",
    "password": "REPLACE_ME"
}
```

3. Open the example app project and in the `BluesnapSdkReactNativeExample` workspace (do not open the project directly) by opening BluesnapSdkReactNativeExample.xcworkspace in xcode  
4. You can then run the example application using xcode without having to implement an example backend. This approach is not for production usage.
5. Clicking the checkout link in the application will start a checkout flow using the native SDK, currently the checkout details such as price are not configurable in this demo mode.
6. if clicking the checkout button does not open the SDK - check the error logs using xcode.

### Example application - Android
WIP

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
