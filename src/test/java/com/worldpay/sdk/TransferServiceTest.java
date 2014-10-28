package com.worldpay.sdk;

import com.worldpay.gateway.clearwater.client.ui.dto.response.TransferDetailResponse;
import com.worldpay.gateway.clearwater.client.ui.dto.response.TransferSearchResponse;
import com.worldpay.sdk.util.PropertyUtils;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Test the transfer service interacts correctly with the API
 */
public class TransferServiceTest {

    private TransferService transferService;

    private String merchantId;

    private final String transferId = "TransferId";

    @Before
    public void setup() {
        transferService = new WorldpayRestClient(PropertyUtils.serviceKey()).getTransferService();
        merchantId = PropertyUtils.getProperty("merchantId");
    }

    @Test
    public void shouldSearchTransfers() {
        TransferSearchResponse response = transferService.search(merchantId, 1);
        assertThat("Transfer search", response, is(notNullValue()));
    }

    @Test
    public void shouldGetTransfer() {
        TransferDetailResponse response = transferService.get(transferId);
        assertThat("Transfer detail", response, is(notNullValue()));
    }
}