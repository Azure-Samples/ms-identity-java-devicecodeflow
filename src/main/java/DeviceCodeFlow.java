// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

import com.microsoft.aad.msal4j.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class DeviceCodeFlow {

    final static String PUBLIC_CLIENT_ID = "Enter_the_Application_Id_here";
    final static String AUTHORITY_COMMON = "https://login.microsoftonline.com/common/";
    final static String GRAPH_SCOPE = "https://graph.microsoft.com/user.readbasic.all";

    public static void main(String args[]) throws Exception {
        getAccessTokenByDeviceCodeGrant();

        System.in.read();
    }

    private static void getAccessTokenByDeviceCodeGrant() throws Exception {
        PublicClientApplication app = PublicClientApplication.builder(PUBLIC_CLIENT_ID)
                .authority(AUTHORITY_COMMON)
                .build();

        Consumer<DeviceCode> deviceCodeConsumer = (DeviceCode deviceCode) -> {
            System.out.println(deviceCode.message());
        };

        CompletableFuture<IAuthenticationResult> future = app.acquireToken(
                DeviceCodeFlowParameters.builder(
                        Collections.singleton(GRAPH_SCOPE),
                        deviceCodeConsumer)
                        .build());

        future.handle((res, ex) -> {
            if(ex != null) {
                System.out.println("Oops! We have an exception of type - " + ex.getClass());
                System.out.println("message - " + ex.getMessage());
                return "Unknown!";
            }
            try {
                String usersListFromGraph = getUsersListFromGraph(res.accessToken());
                System.out.println("Users in the Tenant = " + usersListFromGraph);

                System.out.println("Press any key to exit ...");

            } catch (IOException e) {
                e.printStackTrace();
            } finally {

            }
            return res;
        });

        future.join();
    }

    private static String getUsersListFromGraph(String accessToken) throws IOException {
        URL url = new URL("https://graph.microsoft.com/v1.0/users");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Accept","application/json");

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
