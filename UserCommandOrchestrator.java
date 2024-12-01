import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;

public class UserCommandOrchestrator {

    private final String ecommerceUrl = "http://localhost:8080/api/orders";
private final String paymentUrl = "http://localhost:8080/api/payments";
private final String accountingUrl = "http://localhost:8080/api/records";
private final String warehouseUrl = "http://localhost:8080/api/inventory";


    public boolean createUserOrder(Map<String, Object> orderDetails) {
        String orderId = null;
        String paymentId = null;
        String accountingRecordId = null;
        String inventoryUpdateId = null;

        try {
            // Step 1: Create Order in E-Commerce System
            orderId = makeHttpRequest(ecommerceUrl + "/orders", "POST", orderDetails);
            System.out.println("Order created with ID: " + orderId);

            // Step 2: Create Payment in Stripe System
            paymentId = makeHttpRequest(paymentUrl + "/payments", "POST", Map.of("orderId", orderId));
            System.out.println("Payment created with ID: " + paymentId);

            // Step 3: Create Accounting Record
            accountingRecordId = makeHttpRequest(accountingUrl + "/records", "POST", Map.of("orderId", orderId));
            System.out.println("Accounting record created with ID: " + accountingRecordId);

            // Step 4: Update Inventory in Warehouse
            inventoryUpdateId = makeHttpRequest(warehouseUrl + "/inventory", "POST", Map.of("orderId", orderId));
            System.out.println("Inventory updated with ID: " + inventoryUpdateId);

            System.out.println("Order process completed successfully!");
            return true;

        } catch (Exception e) {
            System.err.println("Error occurred: " + e.getMessage());

            // Rollback actions in reverse order
            if (inventoryUpdateId != null)
                cancelAction(warehouseUrl + "/inventory", inventoryUpdateId);
            if (accountingRecordId != null)
                cancelAction(accountingUrl + "/records", accountingRecordId);
            if (paymentId != null)
                cancelAction(paymentUrl + "/payments", paymentId);
            if (orderId != null)
                cancelAction(ecommerceUrl + "/orders", orderId);

            System.err.println(" rollback completed.");
            return false;
        }
    }

    private String makeHttpRequest(String urlString, String method, Map<String, Object> body) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        if (body != null) {
            String jsonBody = mapToJson(body);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes());
                os.flush();
            }
        }

        if (conn.getResponseCode() != 200 && conn.getResponseCode() != 201) {
            throw new Exception("HTTP request failed with code: " + conn.getResponseCode());
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            return parseResponse(response.toString());
        }
    }

    private void cancelAction(String baseUrl, String resourceId) {
        try {
            makeHttpRequest(baseUrl + "/" + resourceId, "DELETE", null);
            System.out.println("Successfully cancelled resource: " + resourceId);
        } catch (Exception e) {
            System.err.println("Failed to cancel resource " + resourceId + ": " + e.getMessage());
        }
    }

    private String mapToJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        map.forEach((key, value) -> json.append("\"").append(key).append("\":\"").append(value).append("\","));
        if (json.length() > 1)
            json.setLength(json.length() - 1); // Remove trailing comma
        json.append("}");
        return json.toString();
    }

    private String parseResponse(String response) {
        // Assume the response is a simple JSON like {"id": "resourceId"}
        return response.replaceAll("[^a-zA-Z0-9]", "").replace("id", "");
    }

    public static void main(String[] args) {
        UserCommandOrchestrator orchestrator = new UserCommandOrchestrator();

        // Simulate order details
        Map<String, Object> orderDetails = Map.of(
                "userId", "USER123",
                "items", "ITEM1,ITEM2",
                "totalAmount", 100.0);

        // Start the saga
        boolean success = orchestrator.createUserOrder(orderDetails);

        if (success) {
            System.out.println("Order placed successfully.");
        } else {
            System.out.println("Order placement failed.");
        }
    }
}
