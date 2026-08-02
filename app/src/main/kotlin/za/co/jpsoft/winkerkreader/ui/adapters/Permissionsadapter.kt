package za.co.jpsoft.winkerkreader.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.databinding.ItemPermissionBinding
import za.co.jpsoft.winkerkreader.utils.permissions.PermissionItem

/**
 * Displays the list of [PermissionItem]s on the permissions screen.
 *
 * Extracted from the inner class PermissionsActivity.PermissionsAdapter.
 * The Activity coupling is replaced by [onRequestPermission] — a callback
 * invoked when the user taps a row or its request button.
 *
 * Usage in PermissionsActivity:
 *
 *   adapter = PermissionsAdapter(permissionsList) { item ->
 *       requestSpecialPermission(item)
 *   }
 *   binding.recyclerViewPermissions.adapter = adapter
 */
class PermissionsAdapter(
    private val items: List<PermissionItem>,
    private val onRequestPermission: (PermissionItem) -> Unit
) : RecyclerView.Adapter<PermissionsAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPermissionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val itemBinding: ItemPermissionBinding
    ) : RecyclerView.ViewHolder(itemBinding.root) {

        fun bind(item: PermissionItem) {
            val context = itemView.context

            itemBinding.tvPermissionName.text = item.name
            itemBinding.tvPermissionDescription.text = item.description

            if (item.isGranted) {
                itemBinding.ivPermissionStatus.setImageResource(
                    android.R.drawable.checkbox_on_background
                )
                itemBinding.ivPermissionStatus.setColorFilter(
                    ContextCompat.getColor(context, android.R.color.holo_green_dark)
                )
                itemBinding.btnRequestPermission.isEnabled = false
                itemBinding.btnRequestPermission.setText(R.string.permission_granted)
            } else {
                itemBinding.ivPermissionStatus.setImageResource(android.R.drawable.ic_delete)
                itemBinding.ivPermissionStatus.setColorFilter(
                    ContextCompat.getColor(context, android.R.color.holo_red_dark)
                )
                itemBinding.btnRequestPermission.isEnabled = true
                itemBinding.btnRequestPermission.setText(R.string.permission_request)
            }

            itemBinding.btnRequestPermission.setOnClickListener {
                if (!item.isGranted) onRequestPermission(item)
            }

            itemBinding.root.setOnClickListener {
                if (!item.isGranted) {
                    onRequestPermission(item)
                } else {
                    Toast.makeText(
                        context,
                        "${item.name} reeds toestemming ontvang.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}