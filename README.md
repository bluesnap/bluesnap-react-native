# bluesnap-sdk-react-native

A BlueSnap SDK React Native Bridge

## Installation

```sh
npm install bluesnap-sdk-react-native
```

## Token
The Bluesnap Native sdks require a short lived token in order to call API. You should obtain the token during runtime from your backend and pass it to the intialization method.

## Example application

to Open the example app project and in the `BluesnapSdkReactNativeExample` project, add a new file called `credentials.plist`. In the file, add 2 strings in the root, one called `BsAPIUser` and the other called `BsAPIPassword`, each of them must be set to their respective values. 
You can then run the example application without haiving to implement an example backend. This approach is not for production usage.


## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
