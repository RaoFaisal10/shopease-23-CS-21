package pk.edu.nfciet.shopease;

/** Run without Android SDK: java -ea pk.edu.nfciet.shopease.ShopMoneyTest */
public class ShopMoneyTest {
    public static void main(String[] args) {
        assert ShopMoney.delivery(0) == 0 : "empty cart";
        assert ShopMoney.delivery(899) == 200 : "small subtotal";
        assert ShopMoney.delivery(4999) == 200 : "below threshold";
        assert ShopMoney.delivery(5000) == 0 : "at threshold";
        assert ShopMoney.delivery(8298) == 0 : "above threshold";
        assert ShopMoney.rs(3499).contains("3,499") : "formatted price";
        System.out.println("PASS: pricing and delivery policy (6 checks)");
    }
}
