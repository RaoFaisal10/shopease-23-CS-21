package pk.edu.nfciet.shopease;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.SetOptions;
import java.util.HashMap;
import java.util.Map;

/** Opens a product by Intent extra and adds quantities atomically to one cart document. */
public class ProductDetailActivity extends AppCompatActivity {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private Product product;
    private int quantity = 1;
    private boolean saving;
    private TextView qtyText, priceText, stockText;
    private MaterialButton addButton, plusButton, minusButton;
    private View content, loading, errorPanel;
    private String productId;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUi.lightBars(this);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) { AppUi.goAndClear(this, LoginActivity.class); return; }
        setContentView(R.layout.activity_product_detail);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        content = findViewById(R.id.detailContent);
        loading = findViewById(R.id.progress);
        errorPanel = findViewById(R.id.errorPanel);
        qtyText = findViewById(R.id.tvQty);
        priceText = findViewById(R.id.tvPrice);
        stockText = findViewById(R.id.tvStock);
        addButton = findViewById(R.id.btnAddToCart);
        plusButton = findViewById(R.id.btnPlus);
        minusButton = findViewById(R.id.btnMinus);
        productId = getIntent().getStringExtra("productId");
        if (productId == null || productId.trim().isEmpty()) {
            Toast.makeText(this, "Product not found", Toast.LENGTH_SHORT).show(); finish(); return;
        }
        findViewById(R.id.btnRetry).setOnClickListener(v -> loadProduct());
        findViewById(R.id.btnViewCart).setOnClickListener(v -> startActivity(new Intent(this, CartActivity.class)));
        minusButton.setOnClickListener(v -> { if (quantity > 1) { quantity--; showQty(); } });
        plusButton.setOnClickListener(v -> { if (product != null && quantity < product.stock) { quantity++; showQty(); } });
        addButton.setOnClickListener(v -> addToCart());
        loadProduct();
    }

    private void loadProduct() {
        loading.setVisibility(View.VISIBLE);
        errorPanel.setVisibility(View.GONE);
        findViewById(R.id.bottomAction).setVisibility(View.GONE);
        content.setVisibility(View.GONE);
        db.collection("products").document(productId).get()
                .addOnSuccessListener(this, doc -> {
                    product = doc.toObject(Product.class);
                    if (product == null) {
                        showError("Product unavailable", "This product may have been removed from the collection.");
                        return;
                    }
                    loading.setVisibility(View.GONE);
                    content.setVisibility(View.VISIBLE);
                    showProduct();
                }).addOnFailureListener(this, e -> {
                    FirestoreUiErrors.log("Read product details", e);
                    showError(FirestoreUiErrors.title(e, "Product"), FirestoreUiErrors.message(e));
                });
    }
    private void showError(String title, String message) {
        ((TextView) findViewById(R.id.tvDetailErrorTitle)).setText(title);
        ((TextView) findViewById(R.id.tvDetailErrorBody)).setText(message);
        loading.setVisibility(View.GONE);
        content.setVisibility(View.GONE);
        findViewById(R.id.bottomAction).setVisibility(View.GONE);
        errorPanel.setVisibility(View.VISIBLE);
    }
    private void showProduct() {
        ((TextView) findViewById(R.id.tvTitle)).setText(product.title == null ? "Product" : product.title);
        ((TextView) findViewById(R.id.tvCategory)).setText(product.category == null ? "CURATED FIND" : product.category.toUpperCase(java.util.Locale.ROOT));
        ((TextView) findViewById(R.id.tvDescription)).setText(product.description == null ? "No description provided." : product.description);
        priceText.setText(ShopMoney.rs(product.price));
        ((TextView) findViewById(R.id.tvBottomPrice)).setText(ShopMoney.rs(product.price * quantity));
        stockText.setText(product.stock <= 0 ? "Sold out" : product.stock < 5 ? "Only " + product.stock + " left" : "In stock · " + product.stock + " available");
        stockText.setTextColor(getColor(product.stock < 5 ? R.color.coral : R.color.success));
        Glide.with(this).load(product.imageUrl).centerCrop()
                .placeholder(R.drawable.bg_product_placeholder).error(R.drawable.bg_product_placeholder)
                .into((ImageView) findViewById(R.id.ivImage));
        quantity = 1;
        showQty();
    }
    private void showQty() {
        if (product != null) findViewById(R.id.bottomAction).setVisibility(View.VISIBLE);
        qtyText.setText(String.valueOf(quantity));
        minusButton.setEnabled(!saving && quantity > 1);
        plusButton.setEnabled(!saving && product != null && quantity < product.stock);
        addButton.setEnabled(!saving && product != null && product.stock > 0);
        addButton.setText(product != null && product.stock == 0 ? "Sold out" : saving ? "Adding to bag…" : "Add to bag");
        if (product != null) ((TextView) findViewById(R.id.tvBottomPrice)).setText(ShopMoney.rs(product.price * quantity));
    }

    private void addToCart() {
        if (saving || product == null || product.stock < 1) return;
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) { AppUi.goAndClear(this, LoginActivity.class); return; }
        saving = true;
        showQty();
        DocumentReference productRef = db.collection("products").document(productId);
        DocumentReference cartRef = db.collection("carts").document(uid).collection("items").document(productId);
        // Transaction reads latest stock and existing quantity before writing one document.
        db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot currentProduct = transaction.get(productRef);
            com.google.firebase.firestore.DocumentSnapshot existingCart = transaction.get(cartRef);
            Long stock = currentProduct.getLong("stock");
            long current = existingCart.exists() && existingCart.getLong("quantity") != null
                    ? existingCart.getLong("quantity") : 0L;
            if (stock == null || quantity > stock || current + quantity > stock) {
                throw new FirebaseFirestoreException("Not enough stock for that quantity", FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }
            Map<String, Object> item = new HashMap<>();
            item.put("title", currentProduct.getString("title"));
            item.put("imageUrl", currentProduct.getString("imageUrl"));
            item.put("price", currentProduct.getLong("price"));
            item.put("quantity", current + quantity);
            transaction.set(cartRef, item, SetOptions.merge());
            return null;
        }).addOnSuccessListener(this, unused -> {
            saving = false;
            showQty();
            AppUi.message(this, "Added to bag · Open the cart to review");
        }).addOnFailureListener(this, error -> {
            saving = false;
            showQty();
            AppUi.message(this, error.getLocalizedMessage() == null ? "Couldn't add to bag" : error.getLocalizedMessage());
        });
    }
    @Override public boolean onSupportNavigateUp() { finish(); return true; }
}
