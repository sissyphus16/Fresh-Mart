import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

// ─────────────────────────────────────────
// INTERFACE
// ─────────────────────────────────────────
interface Purchasable {
    double getPrice();
    boolean isAvailable();
    void purchase(int qty);
}

// ─────────────────────────────────────────
// ABSTRACT CLASS
// ─────────────────────────────────────────
abstract class Product implements Purchasable {
    protected String name;
    protected double price;
    protected int quantity;

    public Product(String name, double price, int quantity) {
        this.name     = name;
        this.price    = price;
        this.quantity = quantity;
    }

    // Abstract method – every subclass must implement its own display
    public abstract void displayDetails();

    // Used by the website: each subclass describes itself
    public abstract String getType();
    public abstract String getInfo();

    @Override
    public double getPrice() { return price; }

    @Override
    public boolean isAvailable() { return quantity > 0; }

    @Override
    public void purchase(int qty) {
        if (qty > quantity)
            throw new IllegalArgumentException("Not enough stock for: " + name);
        quantity -= qty;
    }

    public String getName()    { return name; }
    public int    getQuantity(){ return quantity; }

    @Override
    public String toString() {
        return String.format("%-22s ₹%7.2f   (stock: %d)", name, price, quantity);
    }
}

// ─────────────────────────────────────────
// SUBCLASS 1 – Food Items
// ─────────────────────────────────────────
class FoodItem extends Product {
    private String  expiryDate;
    private boolean isOrganic;

    public FoodItem(String name, double price, int quantity,
                    String expiryDate, boolean isOrganic) {
        super(name, price, quantity);
        this.expiryDate = expiryDate;
        this.isOrganic  = isOrganic;
    }

    @Override
    public void displayDetails() {
        System.out.printf("  [FOOD]  %s  | Expiry: %-12s | Organic: %s%n",
                this, expiryDate, isOrganic ? "Yes" : "No");
    }

    @Override
    public String getType() { return "Food"; }

    @Override
    public String getInfo() {
        return (isOrganic ? "Organic" : "Regular") + ", best before " + expiryDate;
    }
}

// ─────────────────────────────────────────
// SUBCLASS 2 – Non-Food Items
// ─────────────────────────────────────────
class NonFoodItem extends Product {
    private String category;
    private String brand;

    public NonFoodItem(String name, double price, int quantity,
                       String category, String brand) {
        super(name, price, quantity);
        this.category = category;
        this.brand    = brand;
    }

    @Override
    public void displayDetails() {
        System.out.printf("  [NON-FOOD] %s  | Category: %-12s | Brand: %s%n",
                this, category, brand);
    }

    @Override
    public String getType() { return "Non-Food"; }

    @Override
    public String getInfo() { return category + " by " + brand; }
}

// ─────────────────────────────────────────
// SUBCLASS 3 – Beverage Items
// ─────────────────────────────────────────
class BeverageItem extends Product {
    private int     volumeMl;
    private boolean isAlcoholic;

    public BeverageItem(String name, double price, int quantity,
                        int volumeMl, boolean isAlcoholic) {
        super(name, price, quantity);
        this.volumeMl    = volumeMl;
        this.isAlcoholic = isAlcoholic;
    }

    @Override
    public void displayDetails() {
        System.out.printf("  [BEVERAGE] %s  | Volume: %dml | Alcoholic: %s%n",
                this, volumeMl, isAlcoholic ? "Yes" : "No");
    }

    @Override
    public String getType() { return "Beverage"; }

    @Override
    public String getInfo() {
        String vol = volumeMl > 0 ? volumeMl + " ml, " : "";
        return vol + (isAlcoholic ? "alcoholic" : "non-alcoholic");
    }
}

// ─────────────────────────────────────────
// CART
// ─────────────────────────────────────────
class Cart {
    // Inner class representing a line in the cart
    static class CartItem {
        Product product;
        int     qty;

        CartItem(Product product, int qty) {
            this.product = product;
            this.qty     = qty;
        }
    }

    private List<CartItem> items = new ArrayList<>();

    public void addItem(Product product, int qty) {
        if (!product.isAvailable())
            throw new IllegalStateException(product.getName() + " is out of stock.");
        if (qty > product.getQuantity())
            throw new IllegalArgumentException("Only " + product.getQuantity()
                    + " unit(s) of '" + product.getName() + "' available.");

        // If already in cart, just bump the qty
        for (CartItem ci : items) {
            if (ci.product == product) {
                ci.qty += qty;
                return;
            }
        }
        items.add(new CartItem(product, qty));
    }

    public void showCart() {
        if (items.isEmpty()) { System.out.println("  Your cart is empty."); return; }
        System.out.println("\n  ┌─────────────────────────────────────────────┐");
        System.out.println("  │                  YOUR CART                  │");
        System.out.println("  ├──────────────────────────────┬──────┬───────┤");
        System.out.printf ("  │ %-28s │ Qty  │  Total│%n", "Item");
        System.out.println("  ├──────────────────────────────┼──────┼───────┤");
        for (CartItem ci : items) {
            System.out.printf("  │ %-28s │  %-4d│ ₹%5.2f│%n",
                    ci.product.getName(), ci.qty,
                    ci.product.getPrice() * ci.qty);
        }
        System.out.println("  ├──────────────────────────────┴──────┼───────┤");
        System.out.printf ("  │                           Grand Total│ ₹%5.2f│%n", getTotal());
        System.out.println("  └─────────────────────────────────────┴───────┘");
    }

    public double getTotal() {
        return items.stream()
                    .mapToDouble(ci -> ci.product.getPrice() * ci.qty)
                    .sum();
    }

    public boolean isEmpty() { return items.isEmpty(); }

    // Used by the website to list the cart lines
    public List<CartItem> getItems() { return items; }

    // Deduct stock when customer confirms purchase
    public void checkout() {
        for (CartItem ci : items)
            ci.product.purchase(ci.qty);
        items.clear();
    }
}

// ─────────────────────────────────────────
// CUSTOMER
// ─────────────────────────────────────────
class Customer {
    private String name;
    private Cart   cart;

    public Customer(String name) {
        this.name = name;
        this.cart = new Cart();
    }

    public String getName() { return name; }
    public Cart   getCart() { return cart;  }

    public void checkout() {
        if (cart.isEmpty()) {
            System.out.println("  Your cart is empty – nothing to checkout!");
            return;
        }
        cart.showCart();
        cart.checkout();
        System.out.printf("%n  Thank you, %s! Your payment of ₹%.2f was successful. 🛍%n",
                name, cart.getTotal());
        // Note: getTotal() returns 0 after checkout(); captured before clear above
        // So we show cart first, then checkout
    }

    // Better checkout that shows total BEFORE clearing
    public double checkoutAndPay() {
        cart.showCart();
        double total = cart.getTotal();
        cart.checkout();
        System.out.printf("%n  ✅ Thank you, %s! Payment of ₹%.2f received. Come again!%n",
                name, total);
        return total;
    }
}

// ─────────────────────────────────────────
// GROCERY STORE
// ─────────────────────────────────────────
class GroceryStore {
    private String          shopName;
    private List<Product>   inventory = new ArrayList<>();
    private Scanner         sc        = new Scanner(System.in);

    public GroceryStore(String shopName) {
        this.shopName = shopName;
        loadInventory();
    }

    public String        getShopName()  { return shopName;  }
    public List<Product> getInventory() { return inventory; }

    // Pre-load products with fixed prices
    private void loadInventory() {
        // Food
        inventory.add(new FoodItem("Basmati Rice (1kg)",     65.00, 50, "2026-12-01", false));
        inventory.add(new FoodItem("Whole Wheat Bread",      35.00, 30, "2026-11-20", false));
        inventory.add(new FoodItem("Organic Tomatoes (500g)",49.00, 40, "2026-11-15", true ));
        inventory.add(new FoodItem("Amul Butter (500g)",     280.00,25, "2027-01-10", false));
        inventory.add(new FoodItem("Fresh Paneer (200g)",    90.00, 20, "2026-11-14", false));
        inventory.add(new FoodItem("Eggs (12 pack)",         84.00, 35, "2026-11-25", false));

        // Beverages
        inventory.add(new BeverageItem("Mango Juice 1L",     60.00, 30, 1000, false));
        inventory.add(new BeverageItem("Mineral Water 500ml",20.00, 60,  500, false));
        inventory.add(new BeverageItem("Cold Coffee Can",    50.00, 25,  250, false));
        inventory.add(new BeverageItem("Green Tea (25 bags)",95.00, 20,    0, false));

        // Non-Food
        inventory.add(new NonFoodItem("Surf Excel (1kg)",   110.00, 40, "Detergent",  "Surf Excel"));
        inventory.add(new NonFoodItem("Colgate Toothpaste", 65.00,  30, "Dental Care","Colgate"));
        inventory.add(new NonFoodItem("Dettol Soap",        45.00,  50, "Personal Care","Dettol"));
        inventory.add(new NonFoodItem("Notebook A4 (100pg)",55.00,  20, "Stationery", "Classmate"));
    }

    private void printBanner() {
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║      🛒  WELCOME TO " + String.format("%-24s", shopName) + " ║");
        System.out.println("╚══════════════════════════════════════════════╝");
    }

    private void displayInventory() {
        System.out.println("\n──────────────────────────────────────────────────────────────────");
        System.out.println("  AVAILABLE PRODUCTS");
        System.out.println("──────────────────────────────────────────────────────────────────");
        System.out.printf("  %-4s  %-22s  %8s  %s%n", "No.", "Name", "Price", "Stock");
        System.out.println("  ────  ──────────────────────  ────────  ─────");
        int idx = 1;
        for (Product p : inventory) {
            System.out.printf("  %-4d  %-22s  ₹%6.2f  %d units%s%n",
                    idx++, p.getName(), p.getPrice(), p.getQuantity(),
                    p.isAvailable() ? "" : "  [OUT OF STOCK]");
        }
        System.out.println("──────────────────────────────────────────────────────────────────");
    }

    private void displayDetailedInventory() {
        System.out.println("\n  ── Detailed Product Catalogue ──");
        int idx = 1;
        for (Product p : inventory) {
            System.out.printf("  %2d. ", idx++);
            p.displayDetails();
        }
    }

    public void run() {
        printBanner();

        System.out.print("\n  Enter your name: ");
        String name     = sc.nextLine().trim();
        Customer customer = new Customer(name.isEmpty() ? "Guest" : name);
        System.out.println("\n  Hello, " + customer.getName() + "! Let's start shopping.\n");

        boolean shopping = true;
        while (shopping) {
            System.out.println("\n  ┌─ MENU ─────────────────────────────┐");
            System.out.println("  │  1. View products                  │");
            System.out.println("  │  2. View detailed product info     │");
            System.out.println("  │  3. Add item to cart               │");
            System.out.println("  │  4. View cart                      │");
            System.out.println("  │  5. Checkout & pay                 │");
            System.out.println("  │  6. Exit                           │");
            System.out.println("  └────────────────────────────────────┘");
            System.out.print("  Your choice: ");

            String input = sc.nextLine().trim();
            switch (input) {
                case "1":
                    displayInventory();
                    break;

                case "2":
                    displayDetailedInventory();
                    break;

                case "3":
                    displayInventory();
                    System.out.print("  Enter product number to add: ");
                    try {
                        int pIdx = Integer.parseInt(sc.nextLine().trim()) - 1;
                        if (pIdx < 0 || pIdx >= inventory.size()) {
                            System.out.println("  Invalid product number.");
                            break;
                        }
                        Product chosen = inventory.get(pIdx);
                        System.out.print("  Enter quantity: ");
                        int qty = Integer.parseInt(sc.nextLine().trim());
                        customer.getCart().addItem(chosen, qty);
                        System.out.printf("  ✔ Added %d x '%s' to your cart.%n",
                                qty, chosen.getName());
                    } catch (NumberFormatException e) {
                        System.out.println("  Please enter a valid number.");
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        System.out.println("  ⚠ " + e.getMessage());
                    }
                    break;

                case "4":
                    customer.getCart().showCart();
                    break;

                case "5":
                    if (customer.getCart().isEmpty()) {
                        System.out.println("  Your cart is empty. Add items first!");
                    } else {
                        customer.checkoutAndPay();
                        shopping = false;
                    }
                    break;

                case "6":
                    System.out.println("\n  Goodbye, " + customer.getName() + "! See you next time. 👋");
                    shopping = false;
                    break;

                default:
                    System.out.println("  Invalid option. Please choose 1–6.");
            }
        }
    }
}

// ─────────────────────────────────────────
// WEB LAYER – serves the same store over HTTP
// ─────────────────────────────────────────
class GroceryWeb {
    private final GroceryStore store;
    // One Customer (and so one Cart) per browser, tracked by a cookie
    private final Map<String, Customer> sessions = new ConcurrentHashMap<>();

    GroceryWeb(GroceryStore store) { this.store = store; }

    static void start(GroceryStore store, int port) throws Exception {
        GroceryWeb app = new GroceryWeb(store);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", app::handle);
        server.start();
        System.out.println(store.getShopName() + " website running on port " + port);
        System.out.println("Local link: http://localhost:" + port);
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            Customer customer = session(ex);
            boolean post = "POST".equals(ex.getRequestMethod());

            if (path.equals("/")) {
                byte[] html = Files.readAllBytes(Paths.get("index.html"));
                send(ex, 200, html, "text/html; charset=utf-8");
            } else if (path.equals("/api/products")) {
                json(ex, 200, productsJson());
            } else if (path.equals("/api/cart")) {
                json(ex, 200, cartJson(customer));
            } else if (path.equals("/api/add") && post) {
                json(ex, 200, add(customer, query(ex)));
            } else if (path.equals("/api/checkout") && post) {
                json(ex, 200, checkout(customer, query(ex)));
            } else {
                json(ex, 404, "{\"ok\":false,\"message\":\"Not found\"}");
            }
        } catch (Exception e) {
            e.printStackTrace();
            json(ex, 500, "{\"ok\":false,\"message\":\"Something went wrong on the server.\"}");
        } finally {
            ex.close();
        }
    }

    // ── actions ──────────────────────────────

    private String add(Customer customer, Map<String, String> q) {
        try {
            int id  = Integer.parseInt(q.get("id"));
            int qty = Integer.parseInt(q.get("qty"));
            if (id < 0 || id >= store.getInventory().size())
                return fail("Unknown product.");
            if (qty < 1)
                return fail("Quantity must be at least 1.");

            Product p = store.getInventory().get(id);
            int inCart = 0;
            for (Cart.CartItem ci : customer.getCart().getItems())
                if (ci.product == p) inCart += ci.qty;
            if (inCart + qty > p.getQuantity())
                return fail("Only " + p.getQuantity() + " unit(s) of '" + p.getName() + "' in stock.");

            customer.getCart().addItem(p, qty);
            return "{\"ok\":true,\"message\":" + str("Added " + qty + " x " + p.getName()) + "}";
        } catch (NumberFormatException e) {
            return fail("Please enter a valid number.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return fail(e.getMessage());
        }
    }

    private String checkout(Customer customer, Map<String, String> q) {
        synchronized (store) {
            Cart cart = customer.getCart();
            if (cart.isEmpty())
                return fail("Your cart is empty. Add items first.");

            // Check stock for every line first, so a failed checkout never half-completes
            for (Cart.CartItem ci : cart.getItems())
                if (ci.qty > ci.product.getQuantity())
                    return fail("Stock changed: only " + ci.product.getQuantity()
                            + " unit(s) of '" + ci.product.getName() + "' left.");

            String name = q.getOrDefault("name", "").trim();
            if (name.isEmpty()) name = "Guest";
            double total = cart.getTotal();
            cart.checkout();
            return "{\"ok\":true,\"total\":" + money(total) + ",\"message\":"
                    + str("Thank you, " + name + "! Payment received.") + "}";
        }
    }

    // ── JSON builders ────────────────────────

    private String productsJson() {
        StringBuilder sb = new StringBuilder("[");
        List<Product> list = store.getInventory();
        for (int i = 0; i < list.size(); i++) {
            Product p = list.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"id\":").append(i)
              .append(",\"name\":").append(str(p.getName()))
              .append(",\"type\":").append(str(p.getType()))
              .append(",\"info\":").append(str(p.getInfo()))
              .append(",\"price\":").append(money(p.getPrice()))
              .append(",\"stock\":").append(p.getQuantity())
              .append("}");
        }
        return sb.append("]").toString();
    }

    private String cartJson(Customer customer) {
        StringBuilder sb = new StringBuilder("{\"items\":[");
        boolean first = true;
        for (Cart.CartItem ci : customer.getCart().getItems()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"name\":").append(str(ci.product.getName()))
              .append(",\"qty\":").append(ci.qty)
              .append(",\"total\":").append(money(ci.product.getPrice() * ci.qty))
              .append("}");
        }
        sb.append("],\"total\":").append(money(customer.getCart().getTotal())).append("}");
        return sb.toString();
    }

    // ── helpers ──────────────────────────────

    private Customer session(HttpExchange ex) {
        String sid = null;
        String cookies = ex.getRequestHeaders().getFirst("Cookie");
        if (cookies != null) {
            for (String part : cookies.split(";")) {
                String c = part.trim();
                if (c.startsWith("sid=")) sid = c.substring(4);
            }
        }
        if (sid == null || !sessions.containsKey(sid)) {
            if (sessions.size() > 5000) sessions.clear();   // keep memory bounded
            sid = UUID.randomUUID().toString();
            sessions.put(sid, new Customer("Guest"));
            ex.getResponseHeaders().add("Set-Cookie", "sid=" + sid + "; Path=/; HttpOnly; SameSite=Lax");
        }
        return sessions.get(sid);
    }

    private Map<String, String> query(HttpExchange ex) throws IOException {
        Map<String, String> m = new HashMap<>();
        String q = ex.getRequestURI().getRawQuery();
        if (q != null) {
            for (String kv : q.split("&")) {
                int i = kv.indexOf('=');
                if (i > 0) m.put(kv.substring(0, i),
                        URLDecoder.decode(kv.substring(i + 1), "UTF-8"));
            }
        }
        return m;
    }

    private String fail(String msg) { return "{\"ok\":false,\"message\":" + str(msg) + "}"; }

    private String money(double d) { return String.format(Locale.ROOT, "%.2f", d); }

    private String str(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                default:   sb.append(c);
            }
        }
        return sb.append("\"").toString();
    }

    private void json(HttpExchange ex, int code, String body) throws IOException {
        send(ex, code, body.getBytes(StandardCharsets.UTF_8), "application/json; charset=utf-8");
    }

    private void send(HttpExchange ex, int code, byte[] body, String type) throws IOException {
        ex.getResponseHeaders().add("Content-Type", type);
        ex.getResponseHeaders().add("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }
}

// ─────────────────────────────────────────
// MAIN
// ─────────────────────────────────────────
public class GroceryShop {
    public static void main(String[] args) throws Exception {
        GroceryStore shop = new GroceryStore("FRESH MART");

        if (args.length > 0 && args[0].equals("console")) {
            shop.run();                                   // original console version
        } else {
            String p = System.getenv("PORT");             // hosts like Render set PORT
            int port = (p == null) ? 8080 : Integer.parseInt(p);
            GroceryWeb.start(shop, port);                 // website
        }
    }
}
