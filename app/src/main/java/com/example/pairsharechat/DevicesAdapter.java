package com.example.pairsharechat;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class DevicesAdapter extends RecyclerView.Adapter<DevicesAdapter.DeviceViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(Device device);
    }

    private final List<Device> deviceList;
    private final OnItemClickListener listener;

    public DevicesAdapter(List<Device> deviceList, OnItemClickListener listener) {
        this.deviceList = deviceList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.device_list_item, parent, false);
        return new DeviceViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        Device device = deviceList.get(position);
        holder.bind(device, listener);
    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }

    public static class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView tvDeviceName, tvDeviceIp;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDeviceName = itemView.findViewById(R.id.tvDeviceName);
            tvDeviceIp = itemView.findViewById(R.id.tvDeviceIp);
        }

        public void bind(final Device device, final OnItemClickListener listener) {
            tvDeviceName.setText(device.getName());
            tvDeviceIp.setText(device.getIp());
            Log.d("DevicesAdapter", "Binding device: " + device.getName() + ", IP: " + device.getIp());
            itemView.setOnClickListener(v -> listener.onItemClick(device));
        }
    }
}