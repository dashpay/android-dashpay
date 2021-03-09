package org.dashevo.examples;

import org.dashevo.Client;
import org.dashevo.dapiclient.model.DocumentQuery;
import org.dashevo.dpp.document.Document;
import org.dashevo.platform.Documents;
import org.dashevo.platform.Platform;
import org.dashevo.client.ClientOptions;

import java.util.ArrayList;
import java.util.List;

public class ShowRegisteredNames {

    static Client sdk;
    public static void main(String [] args) {
        sdk = new Client(ClientOptions.builder().network("testnet").build());

        getDocuments();
    }

    private static void getDocuments() {
        Platform platform = sdk.getPlatform();

        int startAt = 0;
        List<Document> documents = new ArrayList<Document>(0);
        int requests = 0;
        do {
            DocumentQuery queryOpts = new DocumentQuery.Builder().startAt(startAt).build();
            System.out.println(queryOpts.toJSON());

            try {
                documents = platform.getDocuments().get("dpns.domain", queryOpts);

                requests += 1;

                for (Document doc : documents) {
                    System.out.println(
                            "Name: %-20s".format(doc.getData().get("label").toString()) +
                                    " (domain: " + doc.getData().get("normalizedParentDomainName").toString() +
                                    ") Identity: " + doc.getOwnerId()
                    );
                }

                startAt += Documents.DOCUMENT_LIMIT;
            } catch (Exception e) {
                System.out.println("\nError retrieving results (startAt =  $startAt)");
                System.out.println(e.getMessage());
            }
        } while (requests == 0 || documents.size() >= 100);
    }
}
