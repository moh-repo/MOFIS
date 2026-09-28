package com.dilaw.mohe.codes;

/** LU_ACCOUNT_NATURE (V073): CoaAccount.nature, the V041 CHECK constraint. */
@CodeList(value = "account-nature", mode = CodeList.Mode.EXACT)
public final class AccountNature {

    private AccountNature() {
    }

    public static final String ASSET = "ASSET";
    public static final String LIABILITY = "LIABILITY";
    public static final String EQUITY = "EQUITY";
    public static final String REVENUE = "REVENUE";
    public static final String EXPENSE = "EXPENSE";
}
