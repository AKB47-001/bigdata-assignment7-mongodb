import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;

import org.bson.Document;
import org.bson.conversions.Bson;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.*;
import static com.mongodb.client.model.Sorts.*;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Projections;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;

// Class to create collection, insert JSON objects & perform MongoDB queries
public class MongoDB {

    // MongoDB database name, client object, databse object
    public static final String DATABASE_NAME = "mydb";        
    public MongoClient mongoClient;       
    public MongoDatabase db;   
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

    
    // Main Method
    public static void main(String[] args) throws Exception {
        MongoDB qmongo = new MongoDB();
        qmongo.connect();
        
        try {
            if (qmongo.db != null) {
                // Dropping collections before loading new data
                qmongo.db.getCollection("customer").drop();
                qmongo.db.getCollection("orders").drop();
                qmongo.db.getCollection("custorders").drop();
                
                qmongo.load();
                qmongo.loadNest();
                
                // Execute queries
                System.out.println("--- Query1: Customer Name with ID: 1000 ---");
                System.out.println(qmongo.query1(1000));
                
                System.out.println("\n--- Query2[orders]: Order Date for ID 32 is ---");
                System.out.println(qmongo.query2(32));
                
                System.out.println("\n--- Query2 Nested [custorders]: Order Date for ID 32 is ---");
                System.out.println(qmongo.query2Nest(32));
                
                System.out.println("\n--- Query3[orders]: Total Number of Orders are ---");
                System.out.println("Total orders in (orders collection): " + qmongo.query3());
                
                System.out.println("\n--- Query3 Nested [custorders]: Total Number of Orders are ---");
                System.out.println("Total orders in (custorders collection): " + qmongo.query3Nest());
                
                System.out.println("\n--- Query4 [Join]: Top 5 Customers by Total Order Amount are---");
                System.out.println(MongoDB.toString(qmongo.query4()));
                
                System.out.println("\n--- Query4 Nested [custorders]: Top 5 Customers by Total Order Amount are---");
                System.out.println(MongoDB.toString(qmongo.query4Nest()));
            } else {
                System.err.println("Database connection failed. Cannot proceed with data loading and queries.");
            }
        } catch (Exception e) {
            System.err.println("An error occurred during execution: " + e.getMessage());
            e.printStackTrace();
        }
    }

    
    // Connect Function to connect to Mongo database and 
    // returns database object to manipulate for connection
    public MongoDatabase connect() {
        try {
            String url = "mongodb+srv://g24ai2022_db_user:oUBCmkpJrAXZmIFX@cluster0ankit.uykbrge.mongodb.net/";
            mongoClient = MongoClients.create(url);       
            db = mongoClient.getDatabase(DATABASE_NAME);
            System.out.println("Successfully attempted connection to database: " + DATABASE_NAME);
        } catch (Exception ex) {
            System.err.println("Exception in connect: Could not establish MongoDB client connection.");
            ex.printStackTrace();
            db = null; 
        }
        return db;
    }
    
    
    // Helper Methods for Data Loading      
    private List<Document> readCustomerData(String fileName) throws IOException, ParseException {
        List<Document> documents = new ArrayList<>();
        File file = new File(fileName);
        if (!file.exists()) {
            // Check for customer.tbl, which must exist if orders.tbl eventually succeeded.
            // If this throws, it means customer.tbl is still missing.
            throw new IOException("Customer data file not found: " + fileName);
        }
        
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                // TPC-H files are pipe-delimited ('|')
                String[] fields = line.split("\\|"); 
                if (fields.length < 8) continue; 

                // Schema: c_custkey (int), c_name (varchar), c_address (varchar), c_nationkey (int), 
                // c_phone (char), c_acctbal (decimal), c_mktsegment (char), c_comment (char)
                Document customer = new Document()
                    .append("c_custkey", Integer.parseInt(fields[0]))
                    .append("c_name", fields[1])
                    .append("c_address", fields[2])
                    .append("c_nationkey", Integer.parseInt(fields[3]))
                    .append("c_phone", fields[4])
                    .append("c_acctbal", new BigDecimal(fields[5]))
                    .append("c_mktsegment", fields[6])
                    .append("c_comment", fields[7]);
                
                documents.add(customer);
            }
        }
        return documents;
    }
    
    private List<Document> readOrdersData(String fileName) throws IOException, ParseException {
        List<Document> documents = new ArrayList<>();
        File file = new File(fileName);
        if (!file.exists()) {
            throw new IOException("Orders data file not found: " + fileName);
        }
        
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] fields = line.split("\\|"); 
                if (fields.length < 9) continue; 

                // Parse date string into a Date object for better MongoDB date type handling
                Date orderDate = dateFormat.parse(fields[4]); 

                // Schema: o_orderkey (int), o_custkey (int), o_orderstatus (char), o_totalprice (decimal), 
                // o_orderdate (datetime), o_orderpriority (char), o_clerk (char), o_shippriority (int), o_comment (varchar)
                Document order = new Document()
                    .append("o_orderkey", Integer.parseInt(fields[0]))
                    .append("o_custkey", Integer.parseInt(fields[1]))
                    .append("o_orderstatus", fields[2])
                    .append("o_totalprice", new BigDecimal(fields[3]))
                    .append("o_orderdate", orderDate) // Stored as Date
                    .append("o_orderpriority", fields[5])
                    .append("o_clerk", fields[6])
                    .append("o_shippriority", Integer.parseInt(fields[7]))
                    .append("o_comment", fields[8]);
                
                documents.add(order);
            }
        }
        return documents;
    }

    
    // TASK1: load()
    // Load Method to load the TPC-H customer and orders data into separate collections
    public void load() throws Exception {
        System.out.println("\n--- Executing the load() function: Loading data into 'customer' and 'orders' collections... ---");        
        MongoCollection<Document> customerCollection = db.getCollection("customer");
        MongoCollection<Document> ordersCollection = db.getCollection("orders");
        // Loading Customer Data and bulk insert
        List<Document> listCustomers = readCustomerData("data/customer.tbl");
        if (!listCustomers.isEmpty()) {
            customerCollection.insertMany(listCustomers);
        }
        // Loading Orders Data and bulk insert
        List<Document> orders = readOrdersData("data/orders.tbl");
        if (!orders.isEmpty()) {
            ordersCollection.insertMany(orders);
        }        
        System.out.println("Loading complete for 'customer' (" + customerCollection.countDocuments() + " docs) and 'orders' (" + ordersCollection.countDocuments() + " docs).");
    }

    
    // TASK2: loadNest()
    // loadNest Method to load the TPC-H customer and order data into a nested
    // collection called custorders where each document contains the customer 
    // information and all orders for that customer
    public void loadNest() throws Exception {
        System.out.println("\n--- Executing the function loadNest(): Loading data into 'custorders' (nested) collection... ---");        
        MongoCollection<Document> custordersCollection = db.getCollection("custorders");        
        // Getting all customers
        List<Document> allCustomers = readCustomerData("data/customer.tbl");        
        // Getting all orders and group them by customer key (o_custkey)
        List<Document> allOrders = readOrdersData("data/orders.tbl");        
        var ordersByCustKey = allOrders.stream()
            .collect(Collectors.groupingBy(doc -> doc.getInteger("o_custkey")));        
        // Combining them into nested documents
        List<Document> custordersList = new ArrayList<>();
        for (Document customer : allCustomers) {
            int custkey = customer.getInteger("c_custkey");            
            // Use c_custkey as the primary _id, and remove the redundant field.
            customer.remove("c_custkey"); 
            customer.put("_id", custkey);             
            List<Document> orders = ordersByCustKey.getOrDefault(custkey, new ArrayList<>());            
            // Remove o_custkey from nested orders (denormalization)
            for(Document order : orders) {
                order.remove("o_custkey"); 
            }            
            customer.put("orders", orders);
            custordersList.add(customer);
        }
        // Inserting data into 'custorders' using insertMany()
        if (!custordersList.isEmpty()) {
            custordersCollection.insertMany(custordersList);
        }
        System.out.println("Loading completed for 'custorders' (" + custordersCollection.countDocuments() + " docs).");
    }

    
    // TASK3: query1()
    // query1 method that returns the customer name given a customer id using the
    // customer collection
    public String query1(int custkey) {
        System.out.println("\n Executing Query1: Customer name for c_custkey=" + custkey);
        MongoCollection<Document> collection = db.getCollection("customer");        
        Document res = collection.find(eq("c_custkey", custkey))
            .projection(fields(include("c_name"), excludeId()))
            .first();
        if (res != null) {
            return res.getString("c_name");
        }
        return null;
    }

    // TASK4: query2()
    // query2 method that returns the order date for a given order id using the orders collection.
    public String query2(int orderId) {
        System.out.println("\n Executing Query2[orders]: Order date for o_orderkey=" + orderId);
        MongoCollection<Document> collection = db.getCollection("orders");        
        Document res = collection.find(eq("o_orderkey", orderId))
            .projection(fields(include("o_orderdate"), excludeId()))
            .first();
        if (res != null) {
            Object dateObj = res.get("o_orderdate");
            if (dateObj instanceof Date) {                
                return dateFormat.format((Date) dateObj);
            }
            return dateObj != null ? dateObj.toString() : null;
        }
        return null;
    }

    // TASK5: query2Nest()
    // query2Nest method that returns order date for a given order id using the
    // custorders collection.
    public String query2Nest(int orderId) {
        System.out.println("\n Executing Query2 Nested [custorders]: Order date for o_orderkey=" + orderId);
        MongoCollection<Document> collection = db.getCollection("custorders");       
        
        List<Bson> pipeline = Arrays.asList(            
            Aggregates.unwind("$orders"),             
            Aggregates.match(eq("orders.o_orderkey", orderId)),            
            Aggregates.project(fields(include("orders.o_orderdate"), excludeId()))
        );
        Document res = collection.aggregate(pipeline).first();
        if (res != null) {            
            Document nestedOrder = (Document) res.get("orders");
            Object dateObj = nestedOrder.get("o_orderdate");            
            if (dateObj instanceof Date) {
                return dateFormat.format((Date) dateObj);
            }
            return dateObj != null ? dateObj.toString() : null;
        }
        return null;
    }

    
    // TASK6: query3()
    // query3 method that returns the total number of orders using the 
    // orders collection.
    public long query3() {
        System.out.println("\n Executing Query3[orders]: Total number of orders are");
        MongoCollection<Document> ordersCollection = db.getCollection("orders");        
        return ordersCollection.countDocuments();
    }

    // TASK7: query3Nest()
    // query3Nest method that returns the total number of orders using the 
    // custorders collection.
    public long query3Nest() {
        System.out.println("\n Executing Query3 Nested [custorders]: Total number of orders are");
        MongoCollection<Document> custordersCollection = db.getCollection("custorders");    
        
        List<Bson> pipeline = Arrays.asList(            
            Aggregates.unwind("$orders"),
            Aggregates.count("totalOrders")
        );
        
        Document res = custordersCollection.aggregate(pipeline).first();        
        if (res != null) {            
            Number count = (Number) res.get("totalOrders");
            if (count != null) {
                return count.longValue();
            }
        }       
        return 0;
    }
    
    // TASK8: query4()
    // query4 method that that returns the top 5 customers based on total order amount
    // using the customer and orders collections.
    public MongoCursor<Document> query4() {
        System.out.println("\n Executing Query4 [join]: Top 5 customers by total order amount is");
        MongoCollection<Document> customerCollection = db.getCollection("customer");        
        
        List<Bson> pipeline = Arrays.asList(            
            Aggregates.lookup( "orders", "c_custkey", "o_custkey", "customerOrders"),   
            Aggregates.unwind("$customerOrders"),              
            Aggregates.group(
                "$c_custkey", 
                Accumulators.sum("totalOrderAmount", "$customerOrders.o_totalprice"),
                Accumulators.first("c_name", "$c_name") 
            ),            
            Aggregates.sort(descending("totalOrderAmount")),            
            Aggregates.limit(5),            
            Aggregates.project(fields(
                include("c_name", "totalOrderAmount"),
                computed("c_custkey", "$_id"),
                excludeId()
            ))
        );        
        AggregateIterable<Document> res = customerCollection.aggregate(pipeline);
        return res.iterator();
    }

    
    // TASK9: query4Nest()
    // query4Nest method that returns the top 5 customers based on total order
    // amount using the custorders collection.    
    public MongoCursor<Document> query4Nest() {
        System.out.println("\n Executing Query4 Nested [custorders]: Top 5 customers by total order amount is");
        MongoCollection<Document> custordersCollection = db.getCollection("custorders");       
        
        List<Bson> pipeline = Arrays.asList(
            
            Aggregates.unwind("$orders"),           
            Aggregates.group(
                "$_id", 
                Accumulators.sum("totalOrderAmount", "$orders.o_totalprice"),
                Accumulators.first("c_name", "$c_name")
            ),            
            Aggregates.sort(descending("totalOrderAmount")),             
            Aggregates.limit(5),            
            Aggregates.project(fields(
                include("c_name", "totalOrderAmount"),
                computed("c_custkey", "$_id"),
                excludeId()
            ))
        );
        
        AggregateIterable<Document> res = custordersCollection.aggregate(pipeline);
        return res.iterator();
    }
    
 
    
    public MongoDatabase getDb() {
        return db;
    }

    
    public static String toString(MongoCursor<Document> cursor) {
        StringBuilder buf = new StringBuilder();
        int count = 0;
        buf.append("Rows:\n");
        
        if (cursor != null) {
            try {
                while (cursor.hasNext()) {
                    Document obj = cursor.next();
                    buf.append(obj.toJson());
                    buf.append("\n");
                    count++;
                }
            } finally {
                // Ensure the cursor is closed after iteration
                cursor.close();
            }
        }
        buf.append("Number of rows: " + count);
        return buf.toString();
    }
}