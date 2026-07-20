package com.fundpilot.backend.market.client;

import feign.Param;
import feign.RequestLine;

public interface ThsFundEstimateClient {

    @RequestLine("GET /?module=api&controller=index&action=chart&info=vm_fd_{fundCode}&start=0930")
    String fetchEstimateRaw(@Param("fundCode") String fundCode);
}
