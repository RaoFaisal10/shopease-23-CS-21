package pk.edu.nfciet.shopease;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cash-on-delivery checkout; batch commits order plus cart deletions atomically. */
public class CheckoutActivity extends AppCompatActivity {
    private TextInputEditText name, phone, city, address;
    private TextInputLayout nameWrap, phoneWrap, cityWrap, addressWrap;
    private MaterialButton placeButton;
    private View progress;
    private TextView summary, itemCount, deliveryLabel;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private CollectionReference cartRef;
    private List<DocumentSnapshot> cartDocs = new ArrayList<>();
    private boolean placing;
    private boolean loaded;
    private long subtotal;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUi.lightBars(this);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) { AppUi.goAndClear(this, LoginActivity.class); return; }
        setContentView(R.layout.activity_checkout);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        nameWrap = findViewById(R.id.tilName); phoneWrap = findViewById(R.id.tilPhone);
        cityWrap = findViewById(R.id.tilCity); addressWrap = findViewById(R.id.tilAddress);
        name = findViewById(R.id.etName); phone = findViewById(R.id.etPhone);
        city = findViewById(R.id.etCity); address = findViewById(R.id.etAddress);
        placeButton = findViewById(R.id.btnPlaceOrder);
        progress = findViewById(R.id.progress);
        summary = findViewById(R.id.tvSummary);
        itemCount = findViewById(R.id.tvItems);
        deliveryLabel = findViewById(R.id.tvDelivery);
        String uid = FirebaseAuth.getInstance().getUid();
        cartRef = db.collection("carts").document(uid).collection("items");
        placeButton.setOnClickListener(v -> placeOrder());
        // Keep the user's name in Firestore, not a hardcoded form default.
        db.collection("users").document(uid).get().addOnSuccessListener(this, snapshot -> {
            String storedName = snapshot.getString("name");
            if (storedName != null && name.getText() != null && name.getText().length() == 0) name.setText(storedName);
        });
    }
    @Override protected void onResume() {
        super.onResume();
        if (cartRef != null && !placing) refreshCart();
    }
    private void refreshCart() {
        loaded = false;
        placeButton.setEnabled(false);
        summary.setText("Refreshing your bag…");
        cartRef.get(Source.SERVER).addOnSuccessListener(this, snap -> {
            applyCartSnapshot(snap);
            if (cartDocs.isEmpty()) AppUi.message(this, "Your bag is empty. Add something first.");
        }).addOnFailureListener(this, e -> {
            FirestoreUiErrors.log("Read checkout cart", e);
            summary.setText("Bag unavailable — return to your bag to retry.");
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(FirestoreUiErrors.title(e, "Cart"))
                    .setMessage(FirestoreUiErrors.message(e))
                    .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                    .show();
        });
    }
    private void applyCartSnapshot(QuerySnapshot snapshot) {
        cartDocs = snapshot.getDocuments();
        subtotal = 0;
        long items = 0;
        for (DocumentSnapshot doc : cartDocs) {
            Long price = doc.getLong("price");
            Long quantity = doc.getLong("quantity");
            if (price != null && quantity != null && price > 0 && quantity > 0) {
                subtotal += price * quantity;
                items += quantity;
            }
        }
        loaded = true;
        itemCount.setText(items + (items == 1 ? " item" : " items"));
        summary.setText(ShopMoney.rs(subtotal + ShopMoney.delivery(subtotal)));
        deliveryLabel.setText(ShopMoney.deliveryLabel(subtotal));
        placeButton.setEnabled(!placing && subtotal > 0 && !cartDocs.isEmpty());
    }

    private static String value(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }
    private boolean validate() {
        nameWrap.setError(null); phoneWrap.setError(null); cityWrap.setError(null); addressWrap.setError(null);
        if (value(name).isEmpty()) { nameWrap.setError("Enter your full name"); name.requestFocus(); return false; }
        if (!value(phone).matches("03\\d{9}")) { phoneWrap.setError("Enter 11 digits starting with 03"); phone.requestFocus(); return false; }
        if (value(city).isEmpty()) { cityWrap.setError("Enter your city"); city.requestFocus(); return false; }
        if (value(address).isEmpty()) { addressWrap.setError("Enter your street address"); address.requestFocus(); return false; }
        return true;
    }
    private void setPlacing(boolean value) {
        placing = value;
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        placeButton.setEnabled(!value && loaded && subtotal > 0 && !cartDocs.isEmpty());
        placeButton.setText(value ? "Placing your order…" : "Place order");
    }
    private void placeOrder() {
        if (placing || !loaded) return;
        if (!validate()) return;
        if (cartDocs.isEmpty() || subtotal == 0) { AppUi.message(this, "Your bag is empty"); return; }
        setPlacing(true); // blocks a second tap during server re-check and batch commit
        // Re-read from the server before placing, so the cart snapshot is not stale.
        cartRef.get(Source.SERVER).addOnSuccessListener(this, latest -> {
            applyCartSnapshot(latest);
            if (cartDocs.isEmpty() || subtotal <= 0) {
                setPlacing(false);
                AppUi.message(this, "Your bag is empty. Add something first.");
                return;
            }
            commitOrder();
        }).addOnFailureListener(this, error -> {
            setPlacing(false);
            AppUi.message(this, "Could not verify your bag. Please try again.");
        });
    }
    private void commitOrder() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) { setPlacing(false); AppUi.goAndClear(this, LoginActivity.class); return; }
        List<Map<String, Object>> items = new ArrayList<>();
        for (DocumentSnapshot doc : cartDocs) {
            Long unit = doc.getLong("price"), qty = doc.getLong("quantity");
            if (unit == null || qty == null || unit < 0 || qty <= 0) {
                setPlacing(false); AppUi.message(this, "Your bag contains an invalid item."); return;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("productId", doc.getId()); item.put("title", doc.getString("title"));
            item.put("price", unit); item.put("quantity", qty);
            items.add(item);
        }
        Map<String, Object> shipping = new HashMap<>();
        shipping.put("name", value(name)); shipping.put("phone", value(phone));
        shipping.put("city", value(city)); shipping.put("street", value(address));
        long delivery = ShopMoney.delivery(subtotal);
        Map<String, Object> order = new HashMap<>();
        order.put("userId", uid); order.put("items", items);
        order.put("subtotal", subtotal); order.put("deliveryFee", delivery);
        order.put("total", subtotal + delivery); order.put("address", shipping);
        order.put("paymentMethod", "COD"); order.put("status", "placed");
        order.put("createdAt", FieldValue.serverTimestamp());
        WriteBatch batch = db.batch();
        DocumentReference orderRef = db.collection("orders").document();
        batch.set(orderRef, order);
        for (DocumentSnapshot doc : cartDocs) batch.delete(doc.getReference());
        batch.commit().addOnSuccessListener(this, unused -> {
            AppUi.message(this, "Order placed! ID: " + orderRef.getId());
            AppUi.goAndClear(this, ProductListActivity.class);
        }).addOnFailureListener(this, error -> {
            setPlacing(false);
            AppUi.message(this, error.getLocalizedMessage() == null ? "Order failed. Try again." : error.getLocalizedMessage());
        });
    }
    @Override public boolean onSupportNavigateUp() { finish(); return true; }
}
