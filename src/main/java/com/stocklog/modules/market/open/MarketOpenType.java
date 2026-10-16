package com.stocklog.modules.market.open;

public enum MarketOpenType {
    DAY_MARKET,PRE_MARKET,REGULAR_MARKET,AFTER_MARKET,CLOSED;

    public boolean isOpen() {
        return !this.equals(DAY_MARKET) && !this.equals(CLOSED);
    }

    public boolean isRegularMarket() {
        return this.equals(REGULAR_MARKET);
    }

}
