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
package org.dash.wallet.integration.coinbase_integration.viewmodels

import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.bitcoinj.core.Coin
import org.bitcoinj.utils.Fiat
import org.dash.wallet.common.Configuration
import org.dash.wallet.common.WalletDataProvider
import org.dash.wallet.common.data.ExchangeRate
import org.dash.wallet.common.data.SingleLiveEvent
import org.dash.wallet.common.livedata.Event
import org.dash.wallet.common.livedata.NetworkStateInt
import org.dash.wallet.common.services.ExchangeRatesProvider
import org.dash.wallet.common.ui.ConnectivityViewModel
import org.dash.wallet.common.util.GenericUtils
import org.dash.wallet.integration.coinbase_integration.DASH_CURRENCY
import org.dash.wallet.integration.coinbase_integration.model.*
import org.dash.wallet.integration.coinbase_integration.network.ResponseResource
import org.dash.wallet.integration.coinbase_integration.repository.CoinBaseRepositoryInt
import javax.inject.Inject

@ExperimentalCoroutinesApi
@HiltViewModel
class CoinbaseConvertCryptoViewModel @Inject constructor(
    private val coinBaseRepository: CoinBaseRepositoryInt,
    val userPreference: Configuration,
    private val walletDataProvider: WalletDataProvider,
    var exchangeRates: ExchangeRatesProvider,
    var networkState: NetworkStateInt
) : ConnectivityViewModel(networkState) {
    private val _userAccountsInfo: MutableLiveData<List<CoinBaseUserAccountDataUIModel>> = MutableLiveData()

    private val _showLoading: MutableLiveData<Boolean> = MutableLiveData()
    val showLoading: LiveData<Boolean>
        get() = _showLoading

    private val _baseIdForUSDModelCoinBase: MutableLiveData<List<BaseIdForUSDData>> = MutableLiveData()

    private val _swapTradeOrder: MutableLiveData<Event<SwapTradeUIModel>> = MutableLiveData()
    val swapTradeOrder: LiveData<Event<SwapTradeUIModel>>
        get() = _swapTradeOrder

    val swapTradeFailedCallback = SingleLiveEvent<String?>()

    private val _userAccountsWithBalance: MutableLiveData<Event<List<CoinBaseUserAccountDataUIModel>>> = MutableLiveData()
    val userAccountsWithBalance: LiveData<Event<List<CoinBaseUserAccountDataUIModel>>>
        get() = _userAccountsWithBalance

    val userAccountError = SingleLiveEvent<Unit>()

    private val _dashWalletBalance = MutableLiveData<Coin>()
    val dashWalletBalance: LiveData<Coin>
        get() = this._dashWalletBalance

    lateinit var exchangeRate: ExchangeRate

    init {
        getWithdrawalLimit()
        setDashWalletBalance()
        getUserAccountInfo()
        getBaseIdForUSDModel()
    }
    private fun getBaseIdForUSDModel() = viewModelScope.launch(Dispatchers.Main) {
        when (val response = coinBaseRepository.getBaseIdForUSDModel(userPreference.exchangeCurrencyCode!!)) {
            is ResponseResource.Success -> {
                response.value?.data?.let {
                    _baseIdForUSDModelCoinBase.value = it
                }
            }

            is ResponseResource.Failure -> {
            }
        }
    }

    private fun getUserAccountInfo() = viewModelScope.launch(Dispatchers.Main) {
        _showLoading.value = true
        when (val response = coinBaseRepository.getUserAccounts(userPreference.exchangeCurrencyCode!!)) {
            is ResponseResource.Success -> {
                _showLoading.value = false
                _userAccountsInfo.value = response.value
            }

            is ResponseResource.Failure -> {
                _showLoading.value = false
            }
        }
    }

    fun swapTrade(valueToConvert: Fiat, selectedCoinBaseAccount: CoinBaseUserAccountDataUIModel, dashToCrypt: Boolean) = viewModelScope.launch(Dispatchers.Main) {
        _showLoading.value = true

        val source_asset =
            if (dashToCrypt)_baseIdForUSDModelCoinBase.value?.firstOrNull { it.base == DASH_CURRENCY }?.base_id ?: ""
            else _baseIdForUSDModelCoinBase.value?.firstOrNull { it.base == selectedCoinBaseAccount.coinBaseUserAccountData.currency?.code }?.base_id ?: ""
        val target_asset = if (dashToCrypt)_baseIdForUSDModelCoinBase.value?.firstOrNull { it.base == selectedCoinBaseAccount.coinBaseUserAccountData.currency?.code }?.base_id ?: ""
        else
            _baseIdForUSDModelCoinBase.value?.firstOrNull { it.base == DASH_CURRENCY }?.base_id ?: ""

        val tradesRequest = TradesRequest(
            GenericUtils.fiatToStringWithoutCurrencyCode(valueToConvert),
            userPreference.exchangeCurrencyCode!!,
            source_asset = source_asset,
            target_asset = target_asset
        )

        when (val result = coinBaseRepository.swapTrade(tradesRequest)) {
            is ResponseResource.Success -> {

                if (result.value == SwapTradeResponse.EMPTY_SWAP_TRADE) {
                    _showLoading.value = false
                    swapTradeFailedCallback.call()
                } else {
                    _showLoading.value = false

                    result.value.apply {
                        this.assetsBaseID = Pair(source_asset, target_asset)
                        this.inputCurrencyName = if (dashToCrypt)"Dash"
                        else
                            selectedCoinBaseAccount.coinBaseUserAccountData.currency?.name ?: ""
                        this.outputCurrencyName = if (dashToCrypt) selectedCoinBaseAccount.coinBaseUserAccountData.currency?.name ?: ""
                        else
                            "Dash"
                        _swapTradeOrder.value = Event(this)
                    }
                }
            }
            is ResponseResource.Failure -> {
                _showLoading.value = false

                val error = result.errorBody?.string()
                if (error.isNullOrEmpty()) {
                    swapTradeFailedCallback.call()
                } else {
                    val message = CoinbaseErrorResponse.getErrorMessage(error)?.message
                    if (message.isNullOrEmpty()) {
                        swapTradeFailedCallback.call()
                    } else {
                        swapTradeFailedCallback.value = message!!
                    }
                }
            }
        }
    }

    fun getUserWalletAccounts(dashToCrypt: Boolean) {
        val userAccountsWithBalanceList =
            if (dashToCrypt) {
                _userAccountsInfo.value?.filter {
                    isValidCoinBaseAccount(it)
                }
            } else {
                _userAccountsInfo.value?.filter {
                    isValidCoinBaseAccount(it) && it.coinBaseUserAccountData.balance?.amount?.toDouble() != 0.0
                }
            }

        if (userAccountsWithBalanceList.isNullOrEmpty()) {
            userAccountError.call()
        } else {
            _userAccountsWithBalance.value = Event(userAccountsWithBalanceList)
        }
    }

    private fun isValidCoinBaseAccount(
        it: CoinBaseUserAccountDataUIModel
    ) = (
        it.coinBaseUserAccountData.balance?.amount?.toDouble() != null &&
            !it.coinBaseUserAccountData.balance.amount.toDouble().isNaN() &&
            it.coinBaseUserAccountData.type != "fiat" &&
            it.coinBaseUserAccountData.balance.currency != DASH_CURRENCY
        )

    private fun setDashWalletBalance() {
        _dashWalletBalance.value = walletDataProvider.getWalletBalance()
    }

    private fun getWithdrawalLimit() = viewModelScope.launch(Dispatchers.Main) {
        when (val response = coinBaseRepository.getWithdrawalLimit()) {
            is ResponseResource.Success -> {
                val withdrawalLimit = response.value
                exchangeRate = getCurrencyExchangeRate(withdrawalLimit.currency)
            }
            is ResponseResource.Failure -> {
                // todo use case when limit is not fetched
            }
        }
    }

    private suspend fun getCurrencyExchangeRate(currency: String): ExchangeRate {
        return exchangeRates.observeExchangeRate(currency).first()
    }


    private val withdrawalLimitInDash: Double
        get() {
            return if (userPreference.coinbaseUserWithdrawalLimitAmount.isNullOrEmpty()) {
                0.0
            } else {
                val formattedAmount = GenericUtils.formatFiatWithoutComma(userPreference.coinbaseUserWithdrawalLimitAmount)
                val fiatAmount = try {
                    Fiat.parseFiat(userPreference.coinbaseSendLimitCurrency, formattedAmount)
                } catch (x: Exception) {
                    Fiat.valueOf(userPreference.coinbaseSendLimitCurrency, 0)
                }
                val newRate = org.bitcoinj.utils.ExchangeRate(Coin.COIN, exchangeRate.fiat)
                val amountInDash = newRate.fiatToCoin(fiatAmount)
                amountInDash.toPlainString().toDoubleOrZero
            }
        }

    fun isInputGreaterThanLimit(amountInDash: Coin): Boolean {
        return amountInDash.toPlainString().toDoubleOrZero.compareTo(withdrawalLimitInDash) > 0
    }
}
