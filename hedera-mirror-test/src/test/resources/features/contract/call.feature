@contractbase @fullsuite
Feature: eth_call Contract Base Coverage Feature

  @call @web3
  Scenario Outline: Validate eth_call
    Given I successfully create estimate gas contract from contract bytes
    Given I successfully create ERC contract from contract bytes
    Given I successfully create precompile contract from contract bytes
    And lower deviation is 5 and upper deviation is 20
    Then I call function with IERC721Metadata token name
    Then I call function with IERC721Metadata token symbol
    Then I call function with IERC721Metadata token totalSupply
    Then I call function with IERC721 token balanceOf owner
    Then I call function with HederaTokenService isToken token
    Then I call function with HederaTokenService isFrozen token, account
    Then I call function with HederaTokenService isKyc token, account
    Then I call function with HederaTokenService getTokenDefaultFreezeStatus token
    Then I call function with HederaTokenService getTokenDefaultKycStatus token
    Then I call function that test allowance with evm address for spender
    #Here are the tests until ETHCALL-040


