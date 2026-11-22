package pk.edu.nfciet.shopease;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.List;

/** Live Firestore cart; listener starts and stops with the screen lifecycle. */
public class CartActivity extends AppCompatActivity {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private CollectionReference cartRef;
    private ListenerRegistration listener;
    private CartAdapter adapter;
    private long subtotal;
    private TextView subtotalView, deliveryView, totalView, deliveryHint, itemCount;
    private View empty, summary, progress, errorPanel;
    private TextView errorTitle, errorBody;
    private MaterialButton checkout;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUi.lightBars(this);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) { AppUi.goAndClear(this, LoginActivity.class); return; }
        setContentView(R.layout.activity_cart);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        String uid = FirebaseAuth.getInstance().getUid();
        cartRef = db.collection("carts").document(uid).collection("items");
        subtotalView = findViewById(R.id.tvSubtotal);
        deliveryView = findViewById(R.id.tvDelivery);
        totalView = findViewById(R.id.tvTotal);
        deliveryHint = findViewById(R.id.tvDeliveryHint);
        itemCount = findViewById(R.id.tvItemCount);
        empty = findViewById(R.id.emptyPanel);
        summary = findViewById(R.id.summaryPanel);
        progress = findViewById(R.id.progress);
        errorPanel = findViewById(R.id.cartErrorPanel);
        errorTitle = findViewById(R.id.tvCartErrorTitle);
        errorBody = findViewById(R.id.tvCartErrorBody);
        findViewById(R.id.btnCartRetry).setOnClickListener(v -> startCartListener());
        checkout = findViewById(R.id.btnCheckout);
        checkout.setEnabled(false);
        adapter = new CartAdapter(new CartAdapter.Actions() {
            @Override public void changeQuantity(CartItem item, int delta) { updateQuantity(item, delta); }
            @Override public void remove(CartItem item) { removeItem(item); }
        });
        RecyclerView rv = findViewById(R.id.rvCart);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);
        findViewById(R.id.btnBrowse).setOnClickListener(v -> finish());
        checkout.setOnClickListener(v -> {
            if (subtotal <= 0) { AppUi.message(this, "Your bag is empty"); return; }
            startActivity(new Intent(this, CheckoutActivity.class));
        });
    }

    @Override protected void onStart() {
        super.onStart();
        startCartListener();
    }

    private void startCartListener() {
        if (cartRef == null || isFinishing()) return;
        if (listener != null) { listener.remove(); listener = null; }
        errorPanel.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        summary.setVisibility(View.GONE);
        checkout.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        listener = cartRef.addSnapshotListener((snap, e) -> {
            if (isFinishing() || isDestroyed()) return;
            progress.setVisibility(View.GONE);
            if (e != null || snap == null) {
                Exception failure = e != null ? e : new IllegalStateException("No cart snapshot returned");
                FirestoreUiErrors.log("Read cart", failure);
                adapter.setItems(new ArrayList<>());
                subtotal = 0;
                itemCount.setText("Your bag is unavailable");
                empty.setVisibility(View.GONE);
                summary.setVisibility(View.GONE);
                checkout.setEnabled(false);
                errorTitle.setText(FirestoreUiErrors.title(failure, "Cart"));
                errorBody.setText(FirestoreUiErrors.message(failure));
                errorPanel.setVisibility(View.VISIBLE);
                return;
            }
            errorPanel.setVisibility(View.GONE);
            List<CartItem> items = snap.toObjects(CartItem.class);
            adapter.setItems(items);
            updateTotals(items);
        });
    }
    @Override protected void onStop() {
        if (listener != null) { listener.remove(); listener = null; }
        super.onStop();
    }

    private void updateQuantity(CartItem item, int delta) {
        if (item.id == null) return;
        adapter.setBusy(item.id, true);
        DocumentReference itemRef = cartRef.document(item.id);
        DocumentReference productRef = db.collection("products").document(item.id);
        db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot product = transaction.get(productRef);
            com.google.firebase.firestore.DocumentSnapshot current = transaction.get(itemRef);
            Long now = current.getLong("quantity");
            Long stock = product.getLong("stock");
            if (!current.exists() || now == null || stock == null || now + delta < 1 || now + delta > stock) {
                throw new FirebaseFirestoreException("Quantity outside available stock", FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }
            transaction.update(itemRef, "quantity", now + delta);
            return null;
        }).addOnSuccessListener(this, unused -> adapter.setBusy(item.id, false))
                .addOnFailureListener(this, error -> {
                    adapter.setBusy(item.id, false);
                    AppUi.message(this, error.getLocalizedMessage() == null ? "Could not update quantity" : error.getLocalizedMessage());
                });
    }
    private void removeItem(CartItem item) {
        if (item.id == null) return;
        adapter.setBusy(item.id, true);
        cartRef.document(item.id).delete()
                .addOnSuccessListener(this, unused -> AppUi.message(this, "Removed from bag"))
                .addOnFailureListener(this, error -> {
                    adapter.setBusy(item.id, false);
                    AppUi.message(this, "Could not remove item. Try again.");
                });
    }

    private void updateTotals(List<CartItem> items) {
        subtotal = 0;
        long itemQuantity = 0;
        for (CartItem item : items) {
            if (item.price > 0 && item.quantity > 0) subtotal += item.price * item.quantity;
            itemQuantity += Math.max(0, item.quantity);
        }
        boolean isEmpty = items.isEmpty();
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        summary.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        itemCount.setText(itemQuantity + (itemQuantity == 1 ? " item in your bag" : " items in your bag"));
        subtotalView.setText(ShopMoney.rs(subtotal));
        deliveryView.setText(ShopMoney.deliveryLabel(subtotal));
        totalView.setText(ShopMoney.rs(subtotal + ShopMoney.delivery(subtotal)));
        deliveryHint.setText(subtotal >= 5000 ? "Free delivery unlocked — lovely choice!"
                : "Add " + ShopMoney.rs(5000 - subtotal) + " more for free delivery");
        checkout.setEnabled(!isEmpty && subtotal > 0);
    }
    @Override public boolean onSupportNavigateUp() { finish(); return true; }
}
