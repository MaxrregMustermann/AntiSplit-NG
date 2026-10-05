package fork.MaxrregMustermann.AntiSplitNG.main;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import fork.MaxrregMustermann.AntiSplitNG.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AppListArrayAdapter extends ArrayAdapter<AppInfo> implements Filterable {
    private final Context context;
    private final Resources resources;
    private final List<AppInfo> originalAppInfoList;
    public List<AppInfo> filteredAppInfoList;
    private final boolean showIcon;
    private AppInfoFilter filter;

    public AppListArrayAdapter(Context context, Resources resources,
            List<AppInfo> appInfoList, boolean showIcon) {
        super(context, R.layout.list_item, appInfoList);
        this.context = context;
        this.resources = resources;
        this.originalAppInfoList = new ArrayList<>(appInfoList);
        this.filteredAppInfoList = new ArrayList<>(appInfoList);
        this.showIcon = showIcon;
    }

    @Override
    public int getCount() {
        return filteredAppInfoList.size();
    }

    @Override
    public AppInfo getItem(int position) {
        return filteredAppInfoList.get(position);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.list_item, parent, false);
        }

        TextView textView = convertView.findViewById(R.id.text_view);
        ImageView iconView = convertView.findViewById(R.id.icon_view);

        AppInfo appInfo = getItem(position);
        textView.setText(appInfo.name);

        if (showIcon) {
            iconView.setImageDrawable(appInfo.icon);
            iconView.setVisibility(View.VISIBLE);
            iconView.setContentDescription(resources.getString(R.string.app_icon_list_label));
        } else iconView.setVisibility(View.GONE);

        return convertView;
    }

    @Override
    public Filter getFilter() {
        if (filter == null) {
            filter = new AppInfoFilter();
        }
        return filter;
    }

    private class AppInfoFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            if (constraint == null || constraint.length() == 0) {
                results.values = new ArrayList<>(originalAppInfoList);
                results.count = originalAppInfoList.size();
            } else {
                List<AppInfo> filteredItems = new ArrayList<>();
                // Locale.ROOT, not the default locale: with a Turkish device the default locale
                // lowercases "I" to a dotless one and "Instagram" stops matching "instagram".
                String filterPattern = constraint.toString().toLowerCase(Locale.ROOT).trim();
                for (AppInfo appInfo : originalAppInfoList) {
                    if (appInfo.name.toLowerCase(Locale.ROOT).contains(filterPattern)
                            || appInfo.packageName.toLowerCase(Locale.ROOT)
                                    .contains(filterPattern)) {
                        filteredItems.add(appInfo);
                    }
                }
                results.values = filteredItems;
                results.count = filteredItems.size();
            }
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            filteredAppInfoList.clear();
            filteredAppInfoList.addAll((List<AppInfo>) results.values);
            notifyDataSetChanged();
        }
    }
}