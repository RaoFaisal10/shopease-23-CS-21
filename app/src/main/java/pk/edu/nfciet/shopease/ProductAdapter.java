package pk.edu.nfciet.shopease;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.List;

/** RecyclerView reuses card views: only the visible products are inflated. */
public final class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.Holder> {
    public interface OnProductClick { void open(Product product); }
    private final List<Product> items = new ArrayList<>();
    private final OnProductClick listener;
    public ProductAdapter(OnProductClick listener) { this.listener = listener; }

    public void setItems(List<Product> products) {
        items.clear();
        items.addAll(products);
        notifyDataSetChanged();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product, parent, false);
        return new Holder(v);
    }
    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        Product p = items.get(position);
        h.title.setText(p.title == null ? "Product" : p.title);
        h.category.setText(p.category == null ? "CURATED FIND" : p.category.toUpperCase(java.util.Locale.ROOT));
        h.price.setText(ShopMoney.rs(p.price));
        if (p.stock <= 0) {
            h.stock.setText("SOLD OUT");
            h.stock.setTextColor(h.itemView.getContext().getColor(R.color.coral));
        } else if (p.stock < 5) {
            h.stock.setText("ONLY " + p.stock + " LEFT");
            h.stock.setTextColor(h.itemView.getContext().getColor(R.color.coral));
        } else {
            h.stock.setText("IN STOCK");
            h.stock.setTextColor(h.itemView.getContext().getColor(R.color.success));
        }
        Glide.with(h.image).load(p.imageUrl).centerCrop()
                .placeholder(R.drawable.bg_product_placeholder)
                .error(R.drawable.bg_product_placeholder).into(h.image);
        h.card.setOnClickListener(v -> listener.open(p));
    }
    @Override public int getItemCount() { return items.size(); }
    static final class Holder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        ImageView image;
        TextView title, category, price, stock;
        Holder(View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            image = itemView.findViewById(R.id.ivProduct);
            title = itemView.findViewById(R.id.tvProductTitle);
            category = itemView.findViewById(R.id.tvProductCategory);
            price = itemView.findViewById(R.id.tvProductPrice);
            stock = itemView.findViewById(R.id.tvProductStock);
        }
    }
}
