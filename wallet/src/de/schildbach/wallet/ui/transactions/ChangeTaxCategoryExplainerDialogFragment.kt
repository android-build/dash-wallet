/*
 * Copyright (c) 2022. Dash Core Group.
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

package de.schildbach.wallet.ui.transactions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import dagger.hilt.android.AndroidEntryPoint
import de.schildbach.wallet.WalletApplication
import de.schildbach.wallet.ui.TransactionResultViewBinder
import de.schildbach.wallet_test.R
import de.schildbach.wallet_test.databinding.DialogChangeTaxCategoryExplainerBinding
import org.bitcoinj.core.Sha256Hash
import org.dash.wallet.common.transactions.TransactionCategory
import org.dash.wallet.common.transactions.TransactionMetadata
import org.dash.wallet.common.ui.dialogs.OffsetDialogFragment
import org.dash.wallet.common.ui.viewBinding

@AndroidEntryPoint
class ChangeTaxCategoryExplainerDialogFragment : OffsetDialogFragment<ConstraintLayout>() {

    private val binding by viewBinding(DialogChangeTaxCategoryExplainerBinding::bind)
    private val wallet by lazy { WalletApplication.getInstance().wallet }

    private val exampleTxId by lazy { arguments?.get(TX_ID) as Sha256Hash }

    companion object {

        const val TX_ID = "tx_id"

        @JvmStatic
        fun newInstance(exampleTxId: Sha256Hash): ChangeTaxCategoryExplainerDialogFragment {
            val fragment = ChangeTaxCategoryExplainerDialogFragment()
            val args = Bundle()
            args.putSerializable(TX_ID, exampleTxId)
            fragment.arguments = args
            return fragment
        }
    }

    override val forceExpand = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_change_tax_category_explainer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            collapseButton.setOnClickListener {
                dismissAllowingStateLoss()
            }

            val tx = wallet.getTransaction(exampleTxId)
            val transactionResultViewBinder = TransactionResultViewBinder(transactionDetails)
            tx?.apply {
                transactionDetails.findViewById<ImageView>(R.id.transaction_close_btn).isVisible =
                    false
                transactionDetails.findViewById<ImageView>(R.id.close_btn).isVisible = false
                transactionResultViewBinder.bind(this)
                transactionResultViewBinder.setTransactionMetadata(
                    TransactionMetadata(
                        tx.txId,
                        tx.updateTime.time,
                        tx.getValue(wallet),
                        TransactionCategory.fromTransaction(tx.type, tx.getValue(wallet))
                    )
                )
            }
        }
    }
}