# Qortal Project - Q-Apps Documentation

## Introduction

Q-Apps are static web apps written in javascript, HTML, CSS, and other static assets. The key difference between a Q-App and a fully static site is its ability to interact with both the logged-in user and on-chain data. This is achieved using the API described in this document.


# Section 0: Basic QDN concepts

## Introduction to QDN resources
Each published item on QDN (Qortal Data Network) is referred to as a "resource". A resource could contain anything from a few characters of text, to a multi-layered directory structure containing thousands of files.

Resources are stored on-chain, however the data payload is generally stored off-chain, and verified using an on-chain SHA-256 hash.

To publish a resource, a user must first register a name, and then include that name when publishing the data. Accounts without a registered name are unable to publish to QDN from a Q-App at this time.

Owning the name grants update privileges to the data. If that name is later sold or transferred, the permission to update that resource is moved to the new owner.


## Name, service & identifier

Each QDN resource has 3 important fields:
- `name` - the registered name of the account that is publishing the data (which will hold update/edit privileges going forwards).<br /><br />
- `service` - the type of content (e.g. IMAGE or JSON). Different services have different validation rules. See [list of available services](#services).<br /><br />
- `identifier` - an optional string to allow more than one resource to exist for a given name/service combination. For example, the name `QortalDemo` may wish to publish multiple images. This can be achieved by using a different identifier string for each. The identifier is only unique to the name in question, and so it doesn't matter if another name is using the same service and identifier string.


## Shared identifiers

Since an identifier can be used by multiple names, this can be used to the advantage of Q-App developers as it allows for data to be stored in a deterministic location.

An example of this is the user's avatar. This will always be published with service `THUMBNAIL` and identifier `qortal_avatar`, along with the user's name. So, an app can display the avatar of a user just by specifying their name when requesting the data. The same applies when publishing data.


## "Default" resources

A "default" resource refers to one without an identifier. For example, when a website is published via the UI, it will use the user's name and the service `WEBSITE`. These do not have an identifier, and are therefore the "default" website for this name. When requesting or publishing data without an identifier, apps can either omit the `identifier` key entirely, or include `"identifier": "default"` to indicate that the resource(s) being queried or published do not have an identifier.


<a name="services"></a>
## Available service types

Here is a list of currently available services that can be used in Q-Apps:

### Public services ###
The services below are intended to be used for publicly accessible data.

IMAGE,
THUMBNAIL,
VIDEO,
AUDIO,
PODCAST,
VOICE,
ARBITRARY_DATA,
JSON,
DOCUMENT,
LIST,
PLAYLIST,
METADATA,
BLOG,
BLOG_POST,
BLOG_COMMENT,
GIF_REPOSITORY,
ATTACHMENT,
FILE,
FILES,
CHAIN_DATA,
STORE,
PRODUCT,
OFFER,
COUPON,
CODE,
PLUGIN,
EXTENSION,
GAME,
ITEM,
NFT,
DATABASE,
SNAPSHOT,
COMMENT,
CHAIN_COMMENT,
WEBSITE,
APP,
QCHAT_ATTACHMENT,
QCHAT_IMAGE,
QCHAT_AUDIO,
QCHAT_VOICE

### Private services ###
For the services below, data is encrypted for a single recipient, and can only be decrypted using the private key of the recipient's wallet.

QCHAT_ATTACHMENT_PRIVATE,
ATTACHMENT_PRIVATE,
FILE_PRIVATE,
IMAGE_PRIVATE,
VIDEO_PRIVATE,
AUDIO_PRIVATE,
VOICE_PRIVATE,
DOCUMENT_PRIVATE,
MAIL_PRIVATE,
MESSAGE_PRIVATE


## Single vs multi-file resources

Some resources, such as those published with the `IMAGE` or `JSON` service, consist of a single file or piece of data (the image or the JSON string). This is the most common type of QDN resource, especially in the context of Q-Apps. These can be published by supplying a base64-encoded string containing the data.

Other resources, such as those published with the `WEBSITE`, `APP`, or `GIF_REPOSITORY` service, can contain multiple files and directories. Publishing these kinds of files is not yet available for Q-Apps, however it is possible to retrieve multi-file resources that are already published. When retrieving this data (via FETCH_QDN_RESOURCE), a `filepath` must be included to indicate the file that you would like to retrieve. There is no need to specify a filepath for single file resources, as these will automatically return the contents of the single file.


## App-specific data

Some apps may want to make all QDN data for a particular service available. However, others may prefer to only deal with data that has been published by their app (if a specific format/schema is being used for instance).

Identifiers can be used to allow app developers to locate data that has been published by their app. The recommended approach for this is to use the app name as a prefix on all identifiers when publishing data.

For instance, an app called `MyApp` could allow users to publish JSON data. The app could choose to prefix all identifiers with the string `myapp_`, and then use a random string for each published resource (resulting in identifiers such as `myapp_qR5ndZ8v`). Then, to locate data that has potentially been published by users of MyApp, it can later search the QDN database for items with `"service": "JSON"` and `"identifier": "myapp_"`. The SEARCH_QDN_RESOURCES action has a `prefix` option in order to match identifiers beginning with the supplied string.

Note that QDN is a permissionless system, and therefore it's not possible to verify that a resource was actually published by the app. It is recommended that apps validate the contents of the resource to ensure it is formatted correctly, instead of making assumptions.


## Updating a resource

To update a resource, it can be overwritten by publishing with the same `name`, `service`, and `identifier` combination. Note that the authenticated account must currently own the name in order to publish an update.


## Routing

If a non-existent `filepath` is accessed, the default behaviour of QDN is to return a `404: File not found` error. This includes anything published using the `WEBSITE` service.

However, routing is handled differently for anything published using the `APP` service. 

For apps, QDN automatically sends all unhandled requests to the index file (generally index.html). This allows the app to use custom routing, as it is able to listen on any path. If a file exists at a path, the file itself will be served, and so the request won't be sent to the index file.

It's recommended that all apps return a 404 page if a request isn't able to be routed.


# Section 1: Simple links and image loading via HTML

## Section 1a: Linking to other QDN websites / resources

The `qortal://` protocol can be used to access QDN data from within Qortal websites and apps. The basic format is as follows:
```
<a href="qortal://{service}/{name}/{identifier}/{path}">link text</a>
```

However, the system will support the omission of the `identifier` and/or `path` components to allow for simpler URL formats.

A simple link to another website can be achieved with this HTML code:
```
<a href="qortal://WEBSITE/QortalDemo">link text</a>
```

To link to a specific page of another website:
```
<a href="qortal://WEBSITE/QortalDemo/minting-leveling/index.html">link text</a>
```

To link to a standalone resource, such as an avatar
```
<a href="qortal://THUMBNAIL/QortalDemo/qortal_avatar">avatar</a>
```

For cases where you would prefer to explicitly include an identifier (to remove ambiguity) you can use the keyword `default` to access a resource that doesn't have an identifier. For instance:
```
<a href="qortal://WEBSITE/QortalDemo/default">link to root of website</a>
<a href="qortal://WEBSITE/QortalDemo/default/minting-leveling/index.html">link to subpage of website</a>
```


## Section 1b: Linking to other QDN images

The same applies for images, such as displaying an avatar:
```
<img src="qortal://THUMBNAIL/QortalDemo/qortal_avatar" />
```

...or even an image from an entirely different website:
```
<img src="qortal://WEBSITE/AlphaX/assets/img/logo.png" />
```


# Section 2: Integrating a Javascript app

Javascript apps allow for much more complex integrations with Qortal's blockchain data.

## Section 2a: Direct API calls

The standard [Qortal Core API](http://localhost:12391/api-documentation) is available to websites and apps, and can be called directly using a standard AJAX request, such as:
```
async function getNameInfo(name) {
    const response = await fetch("/names/" + name);
    const nameData = await response.json();
    console.log("nameData: " + JSON.stringify(nameData));
}
getNameInfo("QortalDemo");
```

However, this only works for read-only data, such as looking up transactions, names, balances, etc. Also, since the address of the logged in account can't be retrieved from the core, apps can't show personalized data with this approach.


## Section 2b: User interaction via qortalRequest()

To take things a step further, the qortalRequest() function can be used to interact with the user, in order to:

- Request address and public key of the logged in account
- Publish data to QDN
- Send chat messages
- Join groups
- Deploy ATs (smart contracts)
- Send QORT or any supported foreign coin
- Add/remove items from lists

In addition to the above, qortalRequest() also supports many read-only functions that are also available via direct core API calls. Using qortalRequest() helps with futureproofing, as the core APIs can be modified without breaking functionality of existing Q-Apps.


### Making a request

Qortal core will automatically inject the `qortalRequest()` javascript function to all websites/apps, which returns a Promise. This can be used to fetch data or publish data to the Qortal blockchain. This functionality supports async/await, as well as try/catch error handling.

```
async function myfunction() {
    try {
        let res = await qortalRequest({
            action: "GET_ACCOUNT_DATA",
            address: "QZLJV7wbaFyxaoZQsjm6rb9MWMiDzWsqM2"
        });
        console.log(JSON.stringify(res)); // Log the response to the console
    
    } catch(e) {
        console.log("Error: " + JSON.stringify(e));
    }
}
myfunction();
```

### Timeouts

Request timeouts are handled automatically when using qortalRequest(). The timeout value will differ based on the action being used - see `getDefaultTimeout()` in [q-apps.js](src/main/resources/q-apps/q-apps.js) for the current values.

If a request times out it will throw an error - `The request timed out` - which can be handled by the Q-App.

It is also possible to specify a custom timeout using `qortalRequestWithTimeout(request, timeout)`, however this is discouraged. It's more reliable and futureproof to let the core handle the timeout values.


# Section 3: qortalRequest Documentation

## Supported actions

Here is a list of currently supported actions:
- GET_USER_ACCOUNT
- GET_ACCOUNT_DATA
- GET_ACCOUNT_NAMES
- SEARCH_NAMES
- GET_NAME_DATA
- LIST_QDN_RESOURCES
- SEARCH_QDN_RESOURCES
- GET_QDN_RESOURCE_STATUS
- GET_QDN_RESOURCE_PROPERTIES
- GET_QDN_RESOURCE_METADATA
- GET_QDN_RESOURCE_URL
- LINK_TO_QDN_RESOURCE
- FETCH_QDN_RESOURCE
- PUBLISH_QDN_RESOURCE
- PUBLISH_MULTIPLE_QDN_RESOURCES
- DECRYPT_DATA
- SAVE_FILE
- GET_WALLET_BALANCE
- GET_BALANCE
- SEND_COIN
- SEARCH_CHAT_MESSAGES
- SEND_CHAT_MESSAGE
- LIST_GROUPS
- JOIN_GROUP
- DEPLOY_AT
- GET_AT
- GET_AT_DATA
- LIST_ATS
- FETCH_BLOCK
- FETCH_BLOCK_RANGE
- SEARCH_TRANSACTIONS
- GET_PRICE
- GET_LIST_ITEMS
- ADD_LIST_ITEMS
- DELETE_LIST_ITEM

More functionality will be added in the future.

## Example Requests

Here are some example requests for each of the above:

### Get address of logged in account
_Will likely require user approval_
```
let account = await qortalRequest({
     action: "GET_USER_ACCOUNT"
});
let address = account.address;
```

### Get public key of logged in account
_Will likely require user approval_
```
let pubkey = await qortalRequest({
     action: "GET_USER_ACCOUNT"
});
let publicKey = account.publicKey;
```

### Get account data
```
let res = await qortalRequest({
    action: "GET_ACCOUNT_DATA",
    address: "QZLJV7wbaFyxaoZQsjm6rb9MWMiDzWsqM2"
});
```

### Get names owned by account
```
let res = await qortalRequest({
    action: "GET_ACCOUNT_NAMES",
    address: "QZLJV7wbaFyxaoZQsjm6rb9MWMiDzWsqM2"
});
```

### Search names
```
let res = await qortalRequest({
    action: "SEARCH_NAMES",
    query: "search query goes here",
    prefix: false, // Optional - if true, only the beginning of the name is matched
    limit: 100,
    offset: 0,
    reverse: false
});
```

### Get name data
```
let res = await qortalRequest({
    action: "GET_NAME_DATA",
    name: "QortalDemo"
});
```


### List QDN resources
```
let res = await qortalRequest({
    action: "LIST_QDN_RESOURCES",
    service: "THUMBNAIL",
    name: "QortalDemo", // Optional (exact match)
    identifier: "qortal_avatar", // Optional (exact match)
    default: true, // Optional
    includeStatus: false, // Optional - will take time to respond, so only request if necessary
    includeMetadata: false, // Optional - will take time to respond, so only request if necessary
    followedOnly: false, // Optional - include followed names only
    excludeBlocked: false, // Optional - exclude blocked content
    limit: 100,
    offset: 0,
    reverse: true
});
```

### Search QDN resources
```
let res = await qortalRequest({
    action: "SEARCH_QDN_RESOURCES",
    service: "THUMBNAIL",
    query: "search query goes here", // Optional - searches both "identifier" and "name" fields
    identifier: "search query goes here", // Optional - searches only the "identifier" field
    name: "search query goes here", // Optional - searches only the "name" field
    prefix: false, // Optional - if true, only the beginning of fields are matched in all of the above filters
    exactMatchNames: true, // Optional - if true, partial name matches are excluded
    default: false, // Optional - if true, only resources without identifiers are returned
    mode: "LATEST", // Optional - whether to return all resources or just the latest for a name/service combination. Possible values: ALL,LATEST. Default: LATEST
    minLevel: 1, // Optional - whether to filter results by minimum account level
    includeStatus: false, // Optional - will take time to respond, so only request if necessary
    includeMetadata: false, // Optional - will take time to respond, so only request if necessary
    nameListFilter: "QApp1234Subscriptions", // Optional - will only return results if they are from a name included in supplied list
    followedOnly: false, // Optional - include followed names only
    excludeBlocked: false, // Optional - exclude blocked content
    // before: 1683546000000, // Optional - limit to resources created before timestamp
    // after: 1683546000000, // Optional - limit to resources created after timestamp
    limit: 100,
    offset: 0,
    reverse: true
});
```

### Search QDN resources (multiple names)
```
let res = await qortalRequest({
    action: "SEARCH_QDN_RESOURCES",
    service: "THUMBNAIL",
    query: "search query goes here", // Optional - searches both "identifier" and "name" fields
    identifier: "search query goes here", // Optional - searches only the "identifier" field
    names: ["QortalDemo", "crowetic", "AlphaX"], // Optional - searches only the "name" field for any of the supplied names
    prefix: false, // Optional - if true, only the beginning of fields are matched in all of the above filters
    exactMatchNames: true, // Optional - if true, partial name matches are excluded
    default: false, // Optional - if true, only resources without identifiers are returned
    mode: "LATEST", // Optional - whether to return all resources or just the latest for a name/service combination. Possible values: ALL,LATEST. Default: LATEST
    includeStatus: false, // Optional - will take time to respond, so only request if necessary
    includeMetadata: false, // Optional - will take time to respond, so only request if necessary
    nameListFilter: "QApp1234Subscriptions", // Optional - will only return results if they are from a name included in supplied list
    followedOnly: false, // Optional - include followed names only
    excludeBlocked: false, // Optional - exclude blocked content
    // before: 1683546000000, // Optional - limit to resources created before timestamp
    // after: 1683546000000, // Optional - limit to resources created after timestamp
    limit: 100,
    offset: 0,
    reverse: true
});
```

### Fetch QDN single file resource
```
let res = await qortalRequest({
    action: "FETCH_QDN_RESOURCE",
    name: "QortalDemo",
    service: "THUMBNAIL",
    identifier: "qortal_avatar", // Optional. If omitted, the default resource is returned, or you can alternatively use the keyword "default"
    encoding: "base64", // Optional. If omitted, data is returned in raw form
    rebuild: false
});
```

### Fetch file from multi file QDN resource
Data is returned in the base64 format
```
let res = await qortalRequest({
    action: "FETCH_QDN_RESOURCE",
    name: "QortalDemo",
    service: "WEBSITE",
    identifier: "default", // Optional. If omitted, the default resource is returned, or you can alternatively request that using the keyword "default", as shown here
    filepath: "index.html", // Required only for resources containing more than one file
    rebuild: false
});
```

### Get QDN resource status
```
let res = await qortalRequest({
    action: "GET_QDN_RESOURCE_STATUS",
    name: "QortalDemo",
    service: "THUMBNAIL",
    identifier: "qortal_avatar", // Optional
    build: true // Optional - request that the resource is fetched & built in the background
});
```

### Get QDN resource properties
```
let res = await qortalRequest({
    action: "GET_QDN_RESOURCE_PROPERTIES",
    name: "QortalDemo",
    service: "THUMBNAIL",
    identifier: "qortal_avatar" // Optional
});
// Returns: filename, size, mimeType (where available)
```

### Get QDN resource metadata
```
let res = await qortalRequest({
    action: "GET_QDN_RESOURCE_METADATA",
    name: "QortalDemo",
    service: "THUMBNAIL",
    identifier: "qortal_avatar" // Optional
});
```

### Publish a single file to QDN
_Requires user approval_.<br />
Note: this publishes a single, base64-encoded file. Multi-file resource publishing (such as a WEBSITE or GIF_REPOSITORY) is not yet supported via a Q-App. It will be added in a future update.
```
let res = await qortalRequest({
    action: "PUBLISH_QDN_RESOURCE",
    name: "Demo", // Publisher must own the registered name - use GET_ACCOUNT_NAMES for a list
    service: "IMAGE",
    identifier: "myapp-image1234" // Optional
    data64: "base64_encoded_data",
    // filename: "image.jpg", // Optional - to help apps determine the file's type
    // title: "Title", // Optional
    // description: "Description", // Optional
    // category: "TECHNOLOGY", // Optional
    // tag1: "any", // Optional
    // tag2: "strings", // Optional
    // tag3: "can", // Optional
    // tag4: "go", // Optional
    // tag5: "here", // Optional
    // encrypt: true, // Optional - to be used with a private service
    // recipientPublicKey: "publickeygoeshere" // Only required if `encrypt` is set to true
});
```

### Publish multiple resources at once to QDN
_Requires user approval_.<br />
Note: each resource being published consists of a single, base64-encoded file, each in its own transaction. Useful for publishing two or more related things, such as a video and a video thumbnail.
```
let res = await qortalRequest({
    action: "PUBLISH_MULTIPLE_QDN_RESOURCES",
    resources: [
        name: "Demo", // Publisher must own the registered name - use GET_ACCOUNT_NAMES for a list
        service: "IMAGE",
        identifier: "myapp-image1234" // Optional
        data64: "base64_encoded_data",
        // filename: "image.jpg", // Optional - to help apps determine the file's type
        // title: "Title", // Optional
        // description: "Description", // Optional
        // category: "TECHNOLOGY", // Optional
        // tag1: "any", // Optional
        // tag2: "strings", // Optional
        // tag3: "can", // Optional
        // tag4: "go", // Optional
        // tag5: "here", // Optional
        // encrypt: true, // Optional - to be used with a private service
        // recipientPublicKey: "publickeygoeshere" // Only required if `encrypt` is set to true
    ],
    [
        ... more resources here if needed ...
    ]
});
```

### Decrypt encrypted/private data
```
let res = await qortalRequest({
    action: "DECRYPT_DATA",
    encryptedData: 'qortalEncryptedDatabMx4fELNTV+ifJxmv4+GcuOIJOTo+3qAvbWKNY2L1r',
    publicKey: 'publickeygoeshere'
});
// Returns base64 encoded string of plaintext data
```

### Prompt user to save a file to disk
Note: mimeType not required but recommended. If not specified, saving will fail if the mimeType is unable to be derived from the Blob.
```
let res = await qortalRequest({
    action: "SAVE_FILE",
    blob: dataBlob,
    filename: "myfile.pdf",
    mimeType: "application/pdf" // Optional but recommended
});
```


### Get wallet balance (QORT)
_Requires user approval_
```
let res = await qortalRequest({
    action: "GET_WALLET_BALANCE",
    coin: "QORT"
});
```


### Get address or asset balance
```
let res = await qortalRequest({
    action: "GET_BALANCE",
    address: "QZLJV7wbaFyxaoZQsjm6rb9MWMiDzWsqM2"
});
```
```
let res = await qortalRequest({
    action: "GET_BALANCE",
    assetId: 1,
    address: "QZLJV7wbaFyxaoZQsjm6rb9MWMiDzWsqM2"
});
```

### Send QORT to address
_Requires user approval_
```
let res = await qortalRequest({
    action: "SEND_COIN",
    coin: "QORT",
    destinationAddress: "QZLJV7wbaFyxaoZQsjm6rb9MWMiDzWsqM2",
    amount: 1.00000000 // 1 QORT
});
```

### Send foreign coin to address
_Requires user approval_<br />
Note: default fees can be found [here](https://github.com/Qortal/qortal-ui/blob/master/plugins/plugins/core/qdn/browser/browser.src.js#L205-L209).
```
let res = await qortalRequest({
    action: "SEND_COIN",
    coin: "LTC",
    destinationAddress: "LSdTvMHRm8sScqwCi6x9wzYQae8JeZhx6y",
    amount: 1.00000000, // 1 LTC
    fee: 0.00000020 // Optional fee per byte (default fee used if omitted, recommended) - not used for QORT or ARRR
});
```

### Search or list chat messages
```
let res = await qortalRequest({
    action: "SEARCH_CHAT_MESSAGES",
    before: 999999999999999,
    after: 0,
    txGroupId: 0, // Optional (must specify either txGroupId or two involving addresses)
    // involving: ["QZLJV7wbaFyxaoZQsjm6rb9MWMiDzWsqM2", "QSefrppsDCsZebcwrqiM1gNbWq7YMDXtG2"], // Optional (must specify either txGroupId or two involving addresses)
    // reference: "reference", // Optional
    // chatReference: "chatreference", // Optional
    // hasChatReference: true, // Optional
    encoding: "BASE64", // Optional (defaults to BASE58 if omitted)
    limit: 100,
    offset: 0,
    reverse: true
});
```

### Send a group chat message
_Requires user approval_
```
let res = await qortalRequest({
    action: "SEND_CHAT_MESSAGE",
    groupId: 0,
    message: "Test"
});
```

### Send a private chat message
_Requires user approval_
```
let res = await qortalRequest({
    action: "SEND_CHAT_MESSAGE",
    destinationAddress: "QZLJV7wbaFyxaoZQsjm6rb9MWMiDzWsqM2",
    message: "Test"
});
```

### List groups
```
let res = await qortalRequest({
    action: "LIST_GROUPS",
    limit: 100,
    offset: 0,
    reverse: true
});
```

### Join a group
_Requires user approval_
```
let res = await qortalRequest({
    action: "JOIN_GROUP",
    groupId: 100
});
```


### Deploy an AT
_Requires user approval_
```
let res = await qortalRequest({
    action: "DEPLOY_AT",
    creationBytes: "12345", // Must be Base58 encoded
    name: "test name",
    description: "test description",
    type: "test type",
    tags: "test tags",
    amount: 1.00000000, // 1 QORT
    assetId: 0,
    // fee: 0.002 // optional - will use default fee if excluded
});
```

### Get AT info
```
let res = await qortalRequest({
    action: "GET_AT",
    atAddress: "ASRUsCjk6fa5bujv3oWYmWaVqNtvxydpPH"
});
```

### Get AT data bytes (base58 encoded)
```
let res = await qortalRequest({
    action: "GET_AT_DATA",
    atAddress: "ASRUsCjk6fa5bujv3oWYmWaVqNtvxydpPH"
});
```

### List ATs by functionality
```
let res = await qortalRequest({
    action: "LIST_ATS",
    codeHash58: "4KdJETRAdymE7dodDmJbf5d9L1bp4g5Nxky8m47TBkvA",
    isExecutable: true,
    limit: 100,
    offset: 0,
    reverse: true
});
```

### Fetch block by signature
```
let res = await qortalRequest({
    action: "FETCH_BLOCK",
    signature: "875yGFUy1zHV2hmxNWzrhtn9S1zkeD7SQppwdXFysvTXrankCHCz4iyAUgCBM3GjvibbnyRQpriuy1cyu953U1u5uQdzuH3QjQivi9UVwz86z1Akn17MGd5Z5STjpDT7248K6vzMamuqDei57Znonr8GGgn8yyyABn35CbZUCeAuXju"
});
```

### Fetch block by height
```
let res = await qortalRequest({
    action: "FETCH_BLOCK",
    height: "1139850"
});
```

### Fetch a range of blocks
```
let res = await qortalRequest({
    action: "FETCH_BLOCK_RANGE",
    height: "1139800",
    count: 20,
    reverse: false
});
```

### Search transactions
```
let res = await qortalRequest({
    action: "SEARCH_TRANSACTIONS",
    // startBlock: 1139000,
    // blockLimit: 1000,
    txGroupId: 0,
    txType: [
        "PAYMENT",
        "REWARD_SHARE"
    ],
    confirmationStatus: "CONFIRMED",
    limit: 10,
    offset: 0,
    reverse: false
});
```

### Get an estimate of the QORT price
```
let res = await qortalRequest({
    action: "GET_PRICE",
    blockchain: "LITECOIN",
    // maxtrades: 10,
    inverse: true
});
```

### Get URL to load a QDN resource
Note: this returns a "Resource does not exist" error if a non-existent resource is requested.
```
let url = await qortalRequest({
    action: "GET_QDN_RESOURCE_URL",
    service: "THUMBNAIL",
    name: "QortalDemo",
    identifier: "qortal_avatar"
    // path: "filename.jpg" // optional - not needed if resource contains only one file
});
```

### Get URL to load a QDN website
Note: this returns a "Resource does not exist" error if a non-existent resource is requested.
```
let url = await qortalRequest({
    action: "GET_QDN_RESOURCE_URL",
    service: "WEBSITE",
    name: "QortalDemo",
});
```

### Get URL to load a specific file from a QDN website
Note: this returns a "Resource does not exist" error if a non-existent resource is requested.
```
let url = await qortalRequest({
    action: "GET_QDN_RESOURCE_URL",
    service: "WEBSITE",
    name: "AlphaX",
    path: "/assets/img/logo.png"
});
```

### Link/redirect to another QDN website
Note: an alternate method is to include `<a href="qortal://WEBSITE/QortalDemo">link text</a>` within your HTML code.
```
let res = await qortalRequest({
    action: "LINK_TO_QDN_RESOURCE",
    service: "WEBSITE",
    name: "QortalDemo",
});
```

### Link/redirect to a specific path of another QDN website
Note: an alternate method is to include `<a href="qortal://WEBSITE/QortalDemo/minting-leveling/index.html">link text</a>` within your HTML code.
```
let res = await qortalRequest({
    action: "LINK_TO_QDN_RESOURCE",
    service: "WEBSITE",
    name: "QortalDemo",
    path: "/minting-leveling/index.html"
});
```

### Get the contents of a list
_Requires user approval_
```
let res = await qortalRequest({
    action: "GET_LIST_ITEMS",
    list_name: "followedNames"
});
```

### Add one or more items to a list
_Requires user approval_
```
let res = await qortalRequest({
    action: "ADD_LIST_ITEMS",
    list_name: "blockedNames",
    items: ["QortalDemo"]
});
```

### Delete a single item from a list
_Requires user approval_.
Items must be deleted one at a time.
```
let res = await qortalRequest({
    action: "DELETE_LIST_ITEM",
    list_name: "blockedNames",
    item: "QortalDemo"
});
```


# Section 4: Examples

Some example projects can be found [here](https://github.com/Qortal/Q-Apps). These can be cloned and modified, or used as a reference when creating a new app.


## Sample App

Here is a sample application to display the logged-in user's avatar:
```
<html>
<head>
    <script>
        async function showAvatar() {
            try {
                // Get QORT address of logged in account
                let account = await qortalRequest({
                    action: "GET_USER_ACCOUNT"
                });
                let address = account.address;
                console.log("address: " + address);
            
                // Get names owned by this account
                let names = await qortalRequest({
                    action: "GET_ACCOUNT_NAMES",
                    address: address
                });
                console.log("names: " + JSON.stringify(names));
            
                if (names.length == 0) {
                    console.log("User has no registered names");
                    return;
                }
            
                // Download base64-encoded avatar of the first registered name
                let avatar = await qortalRequest({
                    action: "FETCH_QDN_RESOURCE",
                    name: names[0].name,
                    service: "THUMBNAIL",
                    identifier: "qortal_avatar",
                    encoding: "base64"
                });
                console.log("Avatar size: " + avatar.length + " bytes");
            
                // Display the avatar image on the screen
                document.getElementById("avatar").src = "data:image/png;base64," + avatar;
                
            } catch(e) {
                console.log("Error: " + JSON.stringify(e));
            }
        }
    </script>
</head>
<body onload="showAvatar()">
    <img width="500" id="avatar" />
</body>
</html>
```


# Section 5: Testing and Development

Publishing an in-development app to mainnet isn't recommended. There are several options for developing and testing a Q-app before publishing to mainnet:

### Preview mode

Select "Preview" in the UI after choosing the zip. This allows for full Q-App testing without the need to publish any data.


### Testnets

For an end-to-end test of Q-App publishing, you can use the official testnet, or set up a single node testnet of your own (often referred to as devnet) on your local machine. See [Single Node Testnet Quick Start Guide](testnet/README.md#quick-start).


### Debugging

It is recommended that you develop and test in a web browser, to allow access to the javascript console. To do this:
1. Open the UI app, then minimise it.
2. In a Chromium-based web browser, visit: http://localhost:12388/
3. Log in to your account and then preview your app/website.
4. Go to `View > Developer > JavaScript Console`. Here you can monitor console logs, errors, and network requests from your app, in the same way as any other web-app.
