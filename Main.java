import java.util.*;
import java.util.concurrent.*;
import java.io.*;

// ---------------- DATABASE LAYER ----------------
interface DatabaseOperations {
    void connect() throws IOException;
    void insertLog(String msg);
    void disconnect();
}

class DBManager implements DatabaseOperations {
    private boolean connected = false;

    public void connect() {
        connected = true;
        System.out.println("[DB] Connected");
    }

    public void insertLog(String msg) {
        if (connected)
            System.out.println("[DB] LOG: " + msg);
    }

    public void disconnect() {
        connected = false;
        System.out.println("[DB] Disconnected");
    }
}

// ---------------- DAO LAYER ----------------
class StockDAO<T> {
    private final List<T> data = Collections.synchronizedList(new ArrayList<>());
    public void save(T item) { data.add(item); }
    public List<T> getAll() { return data; }
}

// ---------------- MODEL LAYER ----------------
class Stock {
    private final String symbol;
    private double price;

    public Stock(String s, double p) {
        symbol = s;
        price = p;
    }

    public String getSymbol() { return symbol; }
    public double getPrice() { return price; }
    public void setPrice(double p) { price = p; }
}

class TechStock extends Stock {
    public TechStock(String s, double p) {
        super(s, p);
    }
}

class Portfolio {
    private final Map<String, Integer> holdings;
    private final double initialCapital = 100000;

    public Portfolio(Map<String, Integer> h) {
        holdings = new HashMap<>(h);
    }

    public Map<String, Integer> getHoldings() { return holdings; }
    public double getInitialCapital() { return initialCapital; }
}

class Market {
    private final Map<String, Stock> stocks = new HashMap<>();
    private final Random r = new Random();

    public Market() {
        stocks.put("AAPL", new TechStock("AAPL", 185));
        stocks.put("GOOG", new TechStock("GOOG", 135));
        stocks.put("TSLA", new Stock("TSLA", 240));
    }

    public synchronized void applyStress(double vol) {
        for (Stock s : stocks.values()) {
            double price = s.getPrice() * (1 + r.nextGaussian() * vol);
            s.setPrice(Math.max(0.01, Math.round(price * 100) / 100.0));
        }
    }

    public Stock get(String key) {
        return stocks.get(key);
    }
}

// ---------------- SERVICE LAYER ----------------
class RiskModeler {

    private final Portfolio portfolio;
    private final Market market;

    public RiskModeler(Portfolio p, Market m) {
        portfolio = p;
        market = m;
    }

    public double value() {
        return portfolio.getHoldings()
                .entrySet()
                .stream()
                .mapToDouble(e -> market.get(e.getKey()).getPrice() * e.getValue())
                .sum();
    }

    public double monteCarlo(int sims, double vol) {
        if (sims <= 0 || vol <= 0)
            throw new IllegalArgumentException("Invalid simulation parameters");

        double initial = value();
        double maxLoss = 0;

        for (int i = 0; i < sims; i++) {
            Market tempM = new Market();
            RiskModeler temp = new RiskModeler(portfolio, tempM);

            double worst = 0;
            for (int d = 0; d < 10; d++) {
                tempM.applyStress(vol);
                worst = Math.max(worst, initial - temp.value());
            }
            maxLoss = Math.max(maxLoss, worst);
        }
        return (maxLoss / initial) * 100;
    }
}

// ---------------- MAIN / CONTROLLER ----------------
public class Main {

    public static void main(String[] args) {

        DBManager db = new DBManager();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            db.connect();
            db.insertLog("Risk simulation started");

            Market market = new Market();
            Portfolio portfolio = new Portfolio(
                    Map.of("AAPL", 50, "GOOG", 10, "TSLA", 20)
            );

            RiskModeler risk = new RiskModeler(portfolio, market);

            Callable<Double> task = () -> risk.monteCarlo(200, 0.05);
            Future<Double> future = executor.submit(task);

            System.out.println("Simulation running asynchronously...");

            double stressRisk = future.get();
            System.out.println("Stress Risk: " + stressRisk + "%");
            System.out.println(stressRisk > 15 ? "High Risk!" : "Risk Acceptable");

            db.insertLog("Simulation completed");

        } catch (Exception e) {
            System.out.println("Handled Error: " + e.getMessage());
        } finally {
            executor.shutdown();
            db.disconnect();
        }
    }
}
