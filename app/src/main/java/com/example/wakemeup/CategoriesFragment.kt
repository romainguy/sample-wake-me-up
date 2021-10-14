/*
 * MIT License
 *
 * Copyright (c) 2021 Romain Guy
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.example.wakemeup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wakemeup.databinding.FragmentCategoriesBinding
import com.example.wakemeup.databinding.ListItemCategoryBinding

class CategoriesFragment : Fragment() {
    private var _binding: FragmentCategoriesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.categoriesListContainer) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
            insets
        }

        val recyclerView: RecyclerView = binding.categoriesList
        recyclerView.layoutManager = GridLayoutManager(view.context, 2)

        val onClickListener = View.OnClickListener { itemView ->
            val item = itemView.tag as Category
            val bundle = Bundle()
            bundle.putLong(
                WakeupCallsFragment.ARG_CATEGORY_ID,
                item.id
            )
            findNavController().navigate(R.id.show_category_wakeupcalls, bundle)
        }

        setupRecyclerView(recyclerView, onClickListener)
    }

    private fun setupRecyclerView(
        recyclerView: RecyclerView,
        onClickListener: View.OnClickListener
    ) {
        recyclerView.adapter = SimpleItemRecyclerViewAdapter(
            Categories,
            onClickListener
        )
    }

    class SimpleItemRecyclerViewAdapter(
        private val values: List<Category>,
        private val onClickListener: View.OnClickListener
    ) :
        RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ListItemCategoryBinding.inflate(
                LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.categoryTitleView.text = item.name
            holder.categoryImageView.setImageResource(item.photo)

            with(holder.itemView) {
                tag = item
                setOnClickListener(onClickListener)
            }
        }

        override fun getItemCount() = values.size

        inner class ViewHolder(binding: ListItemCategoryBinding) :
            RecyclerView.ViewHolder(binding.root) {
            val categoryTitleView: TextView = binding.categoryTitle
            val categoryImageView: ImageView = binding.categoryImage
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
