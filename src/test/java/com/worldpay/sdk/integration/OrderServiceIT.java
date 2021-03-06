/*
 * Copyright 2013 Worldpay
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.worldpay.sdk.integration;

import com.worldpay.api.client.common.enums.OrderStatus;
import com.worldpay.gateway.clearwater.client.core.dto.CountryCode;
import com.worldpay.gateway.clearwater.client.core.dto.CurrencyCode;
import com.worldpay.gateway.clearwater.client.core.dto.common.Address;
import com.worldpay.gateway.clearwater.client.core.dto.common.Entry;
import com.worldpay.gateway.clearwater.client.core.dto.request.*;
import com.worldpay.gateway.clearwater.client.core.dto.response.CardResponse;
import com.worldpay.gateway.clearwater.client.core.dto.response.OrderResponse;
import com.worldpay.gateway.clearwater.client.core.dto.response.TokenResponse;
import com.worldpay.gateway.clearwater.client.core.exception.WorldpayException;
import com.worldpay.gateway.clearwater.client.ui.dto.order.Transaction;
import com.worldpay.sdk.OrderService;
import com.worldpay.sdk.WorldpayRestClient;
import com.worldpay.sdk.util.HttpUrlConnection;
import com.worldpay.sdk.util.JsonParser;
import com.worldpay.sdk.util.PropertyUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class OrderServiceIT {

    /**
     * Test Master card number.
     */
    private static final String TEST_MASTERCARD_NUMBER = "5555 5555 5555 4444";

    /**
     * Card Verification code.
     */
    private static final String TEST_CVC = "123";

    /**
     * Test OrderCode
     */
    private static final String TEST_ORDER_CODE = "orderCode";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    /**
     * Service under test
     */
    private OrderService orderService;

    @Before
    public void setup() {
        orderService = new WorldpayRestClient(PropertyUtils.serviceKey()).getOrderService();
    }

    /**
     * This test for creating an order with valid token
     */
    @Test
    public void shouldCreateOrderForValidToken() {

        OrderRequest orderRequest = createOrderRequest();
        orderRequest.setToken(createToken());

        OrderResponse response = orderService.create(orderRequest);

        assertThat("Response code", response.getOrderCode(), is(notNullValue()));
        assertThat("Amount", response.getAmount(), is(1999));
        assertThat("Customer identifier", response.getKeyValueResponse().getCustomerIdentifiers(), is(notNullValue()));
        assertThat("Card Type", ((CardResponse) response.getPaymentResponse()).getCardType(),
                   equalTo("MASTERCARD_CREDIT"));
    }

    /**
     * Test for creating 3DS order with valid token and 3DS information.
     */
    @Test
    @Ignore
    public void shouldCreateOrderForValidTokenAndThreeDS() {

        OrderRequest orderRequest = createOrderRequestWithThreeDS();
        orderRequest.setToken(createToken());

        OrderResponse response = orderService.create(orderRequest);
        assertThat("Response code", response.getOrderCode(), is(notNullValue()));
        assertThat("Amount", response.getAmount(), is(1999));
        assertThat("Customer identifier", response.getKeyValueResponse().getCustomerIdentifiers(), is(notNullValue()));
    }

    /**
     * This is the test for creating the 3D Order.
     * This test expects authorize3Ds to return {@link OrderResponse} and order status should be Success.
     */
    @Test
    public void shouldAuthorizeThreeDSOrder() {
        OrderRequest orderRequest = createOrderRequestWithThreeDS();
        orderRequest.setName("3D");
        orderRequest.setToken(createToken());

        OrderResponse response = orderService.create(orderRequest);
        assertThat("Order code", response.getOrderCode(), notNullValue());
        assertThat("Order Status", response.getPaymentStatus(), equalTo(OrderStatus.PRE_AUTHORIZED.toString()));

        OrderAuthorizationRequest orderAuthorizationRequest =
            createOrderAuthorizationRequest(orderRequest.getThreeDSecureInfo(), "IDENTIFIED");
        OrderResponse authorizeRespone = orderService.authorize3Ds(response.getOrderCode(), orderAuthorizationRequest);
        assertThat("Response", authorizeRespone, notNullValue());
        assertThat("Order code", authorizeRespone.getOrderCode(), equalTo(response.getOrderCode()));
        assertThat("Order Status", authorizeRespone.getPaymentStatus(), equalTo(OrderStatus.SUCCESS.toString()));
    }

    /**
     * This is test for testing 3DS order with invalid 3DS relevant information.
     */
    @Test(expected = WorldpayException.class)
    public void shouldThrowExceptionIfThreeDSEnabledButInfoInvalid() {

        OrderRequest orderRequest = createOrderRequestWithThreeDS();
        orderRequest.setThreeDSecureInfo(null);
        orderRequest.setToken(createToken());

        OrderResponse response = orderService.create(orderRequest);
        assertThat("Response code", response.getOrderCode(), is(notNullValue()));
        assertThat("Amount", response.getAmount(), is(1999));
        assertThat("Customer identifier", response.getKeyValueResponse().getCustomerIdentifiers(), is(notNullValue()));
    }

    /**
     * This is the test for full refund an order
     */
    @Test
    public void shouldRefundOrder() {
        OrderRequest orderRequest = createOrderRequest();
        orderRequest.setToken(createToken());

        String orderCode = orderService.create(orderRequest).getOrderCode();
        assertThat("Order code", orderCode, is(notNullValue()));

        orderService.refund(orderCode);
    }

    /**
     * This is the test for partial refund an order
     */
    @Test
    public void shouldPartialRefundOrder() {
        OrderRequest orderRequest = createOrderRequest();
        orderRequest.setToken(createToken());

        String orderCode = orderService.create(orderRequest).getOrderCode();
        assertThat("Order code", orderCode, is(notNullValue()));

        orderService.refund(orderCode, 1);
    }

    /**
     * This is the test for creating an order with invalid token.
     * Expects API error
     */
    @Test
    public void shouldThrowExceptionForInvalidToken() {
        OrderRequest orderRequest = createOrderRequest();
        orderRequest.setToken("invalid-token");
        try {
            orderService.create(orderRequest);
        } catch (WorldpayException e) {
            assertThat("Valid token", e.getApiError().getCustomCode(), is("TKN_NOT_FOUND"));
        }
    }

    /**
     * This is the test for creating the authorize only Order.
     * This test expects create to return {@link OrderResponse} and order status should be AUTHORIZED.
     */
    @Test
    public void shouldAuthorizeOnlyOrder() {
        OrderRequest orderRequest = createOrderRequest();
        orderRequest.setToken(createToken());
        orderRequest.setAuthorizeOnly(Boolean.TRUE);

        OrderResponse response = orderService.create(orderRequest);
        assertThat("Order code", response.getOrderCode(), notNullValue());
        assertThat("Amount", response.getAmount(), is(0));
        assertThat("Order status", response.getPaymentStatus(), equalTo(OrderStatus.AUTHORIZED.toString()));
    }

    /**
     * This is the test for cancelling the authorize only Order.
     * This test expects create to return {@link OrderResponse} and order status should be CANCELLED.
     */
    @Test
    public void shouldCancelAuthorizeOnlyOrder() {
        OrderRequest orderRequest = createOrderRequest();
        orderRequest.setToken(createToken());
        orderRequest.setAuthorizeOnly(Boolean.TRUE);

        OrderResponse response = orderService.create(orderRequest);
        assertThat("Order code", response.getOrderCode(), notNullValue());
        assertThat("Amount", response.getAmount(), is(0));
        assertThat("Order status", response.getPaymentStatus(), equalTo(OrderStatus.AUTHORIZED.toString()));

        orderService.cancel(response.getOrderCode());
        Transaction authorizedResponse = orderService.findOrder(response.getOrderCode());
        assertThat("Response", authorizedResponse, notNullValue());
        assertThat("Order Response", authorizedResponse.getOrderResponse(), notNullValue());
        assertThat("Status", authorizedResponse.getOrderResponse().getPaymentStatus(),
                   equalTo(OrderStatus.CANCELLED.toString()));
    }

    /**
     * This is the test for partial capture the authorize only Order.
     * This test expects create to return {@link OrderResponse} and order status should be SUCCESS.
     */
    @Test
    public void shouldPartialCaptureAuthorizeOnlyOrder() {
        OrderRequest orderRequest = createOrderRequest();
        orderRequest.setToken(createToken());
        orderRequest.setAuthorizeOnly(Boolean.TRUE);

        OrderResponse response = orderService.create(orderRequest);
        assertThat("Order code", response.getOrderCode(), notNullValue());
        assertThat("Order status", response.getPaymentStatus(), equalTo(OrderStatus.AUTHORIZED.toString()));
        assertThat("Amount", response.getAmount(), is(0));
        assertThat("Authorized amount", response.getAuthorizedAmount(), is(1999));

        CaptureOrderRequest captureOrderRequest = new CaptureOrderRequest();
        captureOrderRequest.setCaptureAmount(900);
        orderService.capture(captureOrderRequest, response.getOrderCode());
        Transaction authorizedResponse = orderService.findOrder(response.getOrderCode());
        assertThat("Response", authorizedResponse, notNullValue());
        assertThat("Order Response", authorizedResponse.getOrderResponse(), notNullValue());
        assertThat("Status", authorizedResponse.getOrderResponse().getPaymentStatus(),
                   equalTo(OrderStatus.SUCCESS.toString()));
        assertThat("Amount", authorizedResponse.getOrderResponse().getAmount(), is(900));
        assertThat("Authorized amount", authorizedResponse.getOrderResponse().getAuthorizedAmount(), is(1999));
    }

    /**
     * This is the test for full capture the authorize only Order.
     * This test expects create to return {@link OrderResponse} and order status should be SUCCESS.
     */
    @Test
    public void shouldFullCaptureAuthorizeOnlyOrder() {
        OrderRequest orderRequest = createOrderRequest();
        orderRequest.setToken(createToken());
        orderRequest.setAuthorizeOnly(Boolean.TRUE);

        OrderResponse response = orderService.create(orderRequest);
        assertThat("Order code", response.getOrderCode(), notNullValue());
        assertThat("Order status", response.getPaymentStatus(), equalTo(OrderStatus.AUTHORIZED.toString()));
        assertThat("Amount", response.getAmount(), is(0));
        assertThat("Authorized amount", response.getAuthorizedAmount(), is(1999));

        CaptureOrderRequest captureOrderRequest = new CaptureOrderRequest();
        orderService.capture(captureOrderRequest, response.getOrderCode());
        Transaction authorizedResponse = orderService.findOrder(response.getOrderCode());
        assertThat("Response", authorizedResponse, notNullValue());
        assertThat("Order Response", authorizedResponse.getOrderResponse(), notNullValue());
        assertThat("Status", authorizedResponse.getOrderResponse().getPaymentStatus(),
                   equalTo(OrderStatus.SUCCESS.toString()));
        assertThat("Amount", authorizedResponse.getOrderResponse().getAmount(), is(1999));
        assertThat("Authorized amount", authorizedResponse.getOrderResponse().getAuthorizedAmount(), is(1999));
    }

    /**
     * This is the test for over capture the authorize only Order.
     * This test expects API error.
     */
    @Test
    public void shouldExcessCaptureAuthorizeOnlyOrder() {
        expectedException.expect(WorldpayException.class);
        expectedException.expectMessage("API error: Capture amount cannot be more than authorized order amount");
        OrderRequest orderRequest = createOrderRequest();
        orderRequest.setToken(createToken());
        orderRequest.setAuthorizeOnly(Boolean.TRUE);

        OrderResponse response = orderService.create(orderRequest);
        assertThat("Order code", response.getOrderCode(), notNullValue());
        assertThat("Order status", response.getPaymentStatus(), equalTo(OrderStatus.AUTHORIZED.toString()));
        assertThat("Amount", response.getAmount(), is(0));
        assertThat("Authorized amount", response.getAuthorizedAmount(), is(1999));

        CaptureOrderRequest captureOrderRequest = new CaptureOrderRequest();
        captureOrderRequest.setCaptureAmount(2000);
        orderService.capture(captureOrderRequest, response.getOrderCode());
    }

    /**
     * Create an order request with three DS enabled
     *
     * @return {@link OrderRequest}
     */
    private OrderRequest createOrderRequestWithThreeDS() {
        OrderRequest orderRequest = createOrderRequest();
        orderRequest.setIs3DSOrder(true);

        ThreeDSecureInfo threeDSecureInfo = createThreeDsSecureInfo();
        orderRequest.setThreeDSecureInfo(threeDSecureInfo);

        return orderRequest;
    }

    /**
     * Create a test ThreeDSecureInfo
     *
     * @return the test ThreeDSecureInfo
     */
    private ThreeDSecureInfo createThreeDsSecureInfo() {
        ThreeDSecureInfo threeDSecureInfo = new ThreeDSecureInfo();
        threeDSecureInfo.setShopperAcceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        threeDSecureInfo.setShopperIpAddress("195.35.90.111");
        threeDSecureInfo.setShopperSessionId("021ui8ib1");
        threeDSecureInfo.setShopperUserAgent(
            "Mozilla/5.0 (Windows; U; Windows NT 5.1; en-GB; rv:1.9.1.5) Gecko/20091102 Firefox/3.5.5 (.NET CLR 3.5.30729)");
        return threeDSecureInfo;
    }

    /**
     * Create a test OrderAuthorizationRequest
     *
     * @return return the test OrderAuthorizationRequest
     */
    private OrderAuthorizationRequest createOrderAuthorizationRequest(ThreeDSecureInfo threeDSecureInfo,
                                                                      String threeDsResponseCode) {
        OrderAuthorizationRequest orderAuthorizationRequest = new OrderAuthorizationRequest();
        orderAuthorizationRequest.setThreeDSResponseCode(threeDsResponseCode);
        orderAuthorizationRequest.setThreeDSecureInfo(threeDSecureInfo);
        return orderAuthorizationRequest;
    }

    /**
     * Create a test OrderRequest
     *
     * @return {@link OrderRequest}
     */
    private OrderRequest createOrderRequest() {
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setAmount(1999);
        orderRequest.setCurrencyCode(CurrencyCode.GBP);
        orderRequest.setName("test name");
        orderRequest.setOrderDescription("test description");

        Address address = new Address();
        address.setAddress1("line 1");
        address.setAddress2("line 2");
        address.setCity("city");
        address.setCountryCode(CountryCode.GB);
        address.setPostalCode("AB1 2CD");
        orderRequest.setBillingAddress(address);

        List<Entry> customerIdentifiers = new ArrayList<Entry>();
        Entry entry = new Entry("test key 1", "test value 1");
        customerIdentifiers.add(entry);

        orderRequest.setCustomerIdentifiers(customerIdentifiers);
        return orderRequest;
    }

    /**
     * Create a token
     *
     * @return token
     */
    private String createToken() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientKey(PropertyUtils.getProperty("clientKey"));

        CardRequest cardRequest = new CardRequest();
        cardRequest.setCardNumber(TEST_MASTERCARD_NUMBER);
        cardRequest.setCvc(TEST_CVC);
        cardRequest.setName("javalib client");
        cardRequest.setExpiryMonth(2);
        cardRequest.setExpiryYear(2018);

        tokenRequest.setPaymentMethod(cardRequest);

        final String json = JsonParser.toJson(tokenRequest);

        String fullUri = PropertyUtils.getProperty("tokenUrl");
        HttpURLConnection httpURLConnection = HttpUrlConnection.getConnection(fullUri);
        try {
            httpURLConnection.setRequestMethod("POST");
            DataOutputStream dataOutputStream = new DataOutputStream(httpURLConnection.getOutputStream());
            dataOutputStream.writeBytes(json);

            TokenResponse tokenResponse = JsonParser.toObject(httpURLConnection.getInputStream(), TokenResponse.class);

            return tokenResponse.getToken();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
