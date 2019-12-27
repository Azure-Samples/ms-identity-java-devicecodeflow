// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

import com.microsoft.aad.msal4j.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class DeviceCodeFlow {

    private final static String PUBLIC_CLIENT_ID = "Enter_the_Application_Id_here";
    private final static String AUTHORITY_COMMON = "https://login.microsoftonline.com/common/";
    private final static String GRAPH_SCOPE = "https://graph.microsoft.com/user.readbasic.all";
    private final static String GRAPH_USERS_ENDPOINT = "https://graph.microsoft.com/v1.0/users";

    public static void main(String args[]) throws Exception {

        // Get access token from Azure Active Directory
        IAuthenticationResult authenticationResult = getAccessToken();

        // Use access token from authentication result to call Microsoft Graph.
        String usersListFromGraph = getUsersListFromGraph(authenticationResult.accessToken());

        System.out.println("Users in the Tenant - " + usersListFromGraph);
        System.out.println("Press any key to exit ...");
        System.in.read();
    }

    private static IAuthenticationResult getAccessToken() throws Exception {

        PublicClientApplication app = PublicClientApplication
                .builder(PUBLIC_CLIENT_ID)
                .authority(AUTHORITY_COMMON)
                .build();

        // Check if there are any accounts in the token cache. In the case of this sample, we are not loading a token
        // cache from disk (see aka.ms/msal4j-tokencache) there will be no accounts in the token cache.
        // Regardless, the sample aims to demonstrate the recommended practice of first attempting
        // to acquire token silently and if that fails, falling back to acquiring a token interactively
        // (in this case, via Oauth2 device code flow)
        Set<IAccount> accountsInTokenCache = app.getAccounts().join();

        IAuthenticationResult authenticationResult;
        if(!accountsInTokenCache.isEmpty()){

            // We select the account that we want to get tokens for. For simplicity, we take the first account
            // in the token cache. In a production application, you would filter to get the desired account
            IAccount account = accountsInTokenCache.iterator().next();
            //If the application has an account in the token cache, we will try to acquire a token silently.
            authenticationResult = getAccessTokenSilently(app, account);
        } else {
            // If token cache is empty, we ask the user to put in their credentials in to the
            // sign in prompt and consent to the requested permissions.
            authenticationResult = getAccessTokenByDeviceCodeGrant(app);
    }

        return authenticationResult;
    }

    private static IAuthenticationResult getAccessTokenSilently(
            PublicClientApplication app,
            IAccount account) {

        IAuthenticationResult result;
        try {

            SilentParameters parameters = SilentParameters
                    .builder(Collections.singleton(GRAPH_SCOPE), account)
                    .build();

            result = app.acquireTokenSilently(parameters).join();

        } catch(Exception ex){

            // If acquiring a token silently failed, lets try acquire token interactively
            if(ex instanceof MsalException){
                return getAccessTokenByDeviceCodeGrant(app);
            }

            System.out.println("Oops! We have an exception of type - " + ex.getClass());
            System.out.println("Exception message - " + ex.getMessage());
            throw new RuntimeException(ex);
        }

        return result;
    }

    private static IAuthenticationResult getAccessTokenByDeviceCodeGrant(PublicClientApplication app) {

        Consumer<DeviceCode> deviceCodeConsumer = (DeviceCode deviceCode) -> System.out.println(deviceCode.message());

        DeviceCodeFlowParameters deviceCodeFlowParameters = DeviceCodeFlowParameters
                .builder(Collections.singleton(GRAPH_SCOPE), deviceCodeConsumer)
                .build();

        CompletableFuture<IAuthenticationResult> future = app.acquireToken(deviceCodeFlowParameters);

        future.handle((res, ex) -> {
            if(ex != null) {
                System.out.println("Oops! We have an exception of type - " + ex.getClass());
                System.out.println("Exception message - " + ex.getMessage());
                throw new RuntimeException(ex);
            }

            return res;
        });

        return future.join();
    }

    private static String getUsersListFromGraph(String accessToken) throws IOException {
        URL url = new URL(GRAPH_USERS_ENDPOINT);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Accept", "application/json");

        int httpResponseCode = conn.getResponseCode();
        if(httpResponseCode == 200) {

            StringBuilder response;
            try(BufferedReader in = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))){

                String inputLine;
                response = new StringBuilder();
                while (( inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
            }
            return response.toString();
        } else {
            return String.format("Connection returned HTTP code: %s with message: %s",
                    httpResponseCode, conn.getResponseMessage());
        }
    }
}
