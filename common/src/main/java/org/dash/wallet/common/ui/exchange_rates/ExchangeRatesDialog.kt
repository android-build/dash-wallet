/*
 * Copyright 2021 Dash Core Group.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.dash.wallet.common.ui.exchange_rates

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.dash.wallet.common.Constants
import org.dash.wallet.common.R
import org.dash.wallet.common.data.ExchangeRate
import org.dash.wallet.common.databinding.DialogOptionPickerBinding
import org.dash.wallet.common.ui.decorators.ListDividerDecorator
import org.dash.wallet.common.ui.dialogs.OffsetDialogFragment
import org.dash.wallet.common.ui.radio_group.IconSelectMode
import org.dash.wallet.common.ui.radio_group.IconifiedViewItem
import org.dash.wallet.common.ui.radio_group.RadioGroupAdapter
import org.dash.wallet.common.ui.viewBinding

@AndroidEntryPoint
class ExchangeRatesDialog(
    private val selectedCurrencyCode: String = "USD",
    private val clickListener: (ExchangeRate, Int, DialogFragment) -> Unit
) : OffsetDialogFragment() {
    override val forceExpand: Boolean = true
    private val binding by viewBinding(DialogOptionPickerBinding::bind)
    private val viewModel: ExchangeRatesViewModel by viewModels()
    private var itemList = listOf<IconifiedViewItem>()
    private var didFocusOnSelected = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_option_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.searchTitle.text = getString(R.string.select_currency)

        val adapter = RadioGroupAdapter(0, true) { item, index ->
            viewModel.exchangeRates.value?.firstOrNull {
                it.currencyCode == item.additionalInfo
            }?.let {
                clickListener.invoke(it, index, this)
            }
        }
        val divider = ContextCompat.getDrawable(requireContext(), R.drawable.list_divider)!!
        val decorator = ListDividerDecorator(
            divider,
            showAfterLast = false,
            marginStart = resources.getDimensionPixelOffset(R.dimen.divider_margin_horizontal),
            marginEnd = resources.getDimensionPixelOffset(R.dimen.divider_margin_horizontal)
        )
        binding.contentList.addItemDecoration(decorator)
        binding.contentList.adapter = adapter

        binding.searchQuery.doOnTextChanged { text, _, _, _ ->
            binding.clearBtn.isVisible = !text.isNullOrEmpty()

            adapter.submitList(if (text.isNullOrBlank()) {
                itemList
            } else {
                filterByQuery(itemList, text.toString())
            })
        }

        binding.searchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val inputManager = requireContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputManager.toggleSoftInput(0, 0)
            }

            true
        }

        binding.clearBtn.setOnClickListener {
            binding.searchQuery.text.clear()
        }

        viewModel.exchangeRates.observe(viewLifecycleOwner) { rates ->
            itemList = rates.map {
                IconifiedViewItem(
                    it.getCurrencyName(requireContext()),
                    Constants.SEND_PAYMENT_LOCAL_FORMAT.noCode().format(it.fiat).toString(),
                    getFlagFromCurrencyCode(it.currencyCode),
                    IconSelectMode.None,
                    it.currencyCode
                )
            }.sortedBy { it.title }

            if (!didFocusOnSelected) {
                lifecycleScope.launch {
                    delay(250)
                    adapter.submitList(itemList)
                    val selectedRateIndex = itemList.indexOfFirst { it.additionalInfo == selectedCurrencyCode }
                    adapter.selectedIndex = selectedRateIndex
                    binding.contentList.scrollToPosition(selectedRateIndex)
                    didFocusOnSelected = true
                }
            } else {
                val list = if (binding.searchQuery.text.isNullOrBlank()) {
                    itemList
                } else {
                    filterByQuery(itemList, binding.searchQuery.text.toString())
                }
                val layoutManager = binding.contentList.layoutManager as LinearLayoutManager
                val scrollPosition = layoutManager.findFirstVisibleItemPosition()
                adapter.submitList(list)
                binding.contentList.scrollToPosition(scrollPosition)
            }
        }
    }

    override fun dismiss() {
        lifecycleScope.launch {
            delay(300)
            super.dismiss()
        }
    }

    private fun filterByQuery(items: List<IconifiedViewItem>, query: String): List<IconifiedViewItem> {
        return items.filter {
            it.title.lowercase().contains(query.lowercase()) ||
                    it.additionalInfo?.lowercase()?.contains(query.lowercase()) == true
        }
    }

    private fun getFlagFromCurrencyCode(currencyCode: String): Int {
        val resourceId = resources.getIdentifier(
            "currency_code_" + currencyCode.lowercase(),
            "drawable", requireContext().packageName
        )
        return if (resourceId == 0) R.drawable.ic_default_flag else resourceId
    }
}