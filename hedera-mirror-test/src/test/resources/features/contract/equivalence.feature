@contractbase @fullsuite @equivalence
Feature: in-equivalence tests

  Scenario Outline: Validate in-equivalence system accounts for balance, extcodesize, extcodecopy, extcodehash
    Given I successfully create selfdestruct contract
    Given I successfully create equivalence call contract
    Then the mirror node REST API should return status 200 for the contracts creation
    Then I execute selfdestruct and set beneficiary to invalid <account> address
    Then I execute balance opcode to system account <account> address would return 0
    Then I verify extcodesize opcode against a system account <account> address returns 0
    Then I verify extcodecopy opcode against a system account <account> address returns empty bytes
    Then I verify extcodehash opcode against a system account <account> address returns empty bytes
    Examples:
      | account   |
      | "0.0.0"   |
      | "0.0.1"   |
      | "0.0.2"   |
      | "0.0.3"   |
      | "0.0.4"   |
      | "0.0.5"   |
      | "0.0.6"   |
      | "0.0.7"   |
      | "0.0.8"   |
      | "0.0.9"   |
      | "0.0.10"  |
      | "0.0.11"  |
      | "0.0.356" |
      | "0.0.357" |
      | "0.0.358" |
      | "0.0.359" |
      | "0.0.360" |
      | "0.0.361" |
      | "0.0.750" |

  Scenario Outline: Validate in-equivalence tests for addresses over 750
    Given I successfully create selfdestruct contract
    Given I successfully create equivalence call contract
    Then the mirror node REST API should return status 200 for the contracts creation
    Then I execute balance opcode against a contract with balance
    Then I execute selfdestruct and set beneficiary to valid "0.0.800" address
    Given I successfully create selfdestruct contract
    Then I execute selfdestruct and set beneficiary to the deleted contract address
    Then I verify extcodesize opcode against a system account "0.0.999" address returns 0
    Then I verify extcodecopy opcode against a system account "0.0.999" address returns empty bytes
    Then I verify extcodehash opcode against a system account "0.0.999" address returns empty bytes

  Scenario Outline: Validate direct calls
    Given I successfully create estimate precompile contract
    Given I successfully create equivalence call contract
    Then I execute directCall to "0.0.0" address without amount
    Then I execute directCall to "0.0.0" address with amount 10000
    #Then I execute directCall to range <account> addresses via HTS Precompile without amount
    #Then I execute directCall to range <accountsMiddleRange> addresses without amount
    #Then I execute directCall to range <accountsMiddleRange> addresses with amount 10000
    Then I execute directCall to address "0.0.1005" with contract without amount
    Then I execute directCall to address above 1000 with invalid contractID without amount
    Then I execute directCall to address above 1000 with contract without amount 1000
    #Then I execute directCall to address "0.0.1005" with contract with amount 100
    Then I execute directCall to address with non-payable contract with amount 500
    Then I execute directCall to address with contract with amount 500 with receiverSig false
    Then I execute directCall to address with contract with amount 400 with receiverSig true
    Then I execute directCall to address with contract with amount 100 with hollow account
    Examples:
      | account   | accountsMiddleRange | accountsUpperRange |
      | "0.0.0"   | "0.0.359"           | "0.0.749"          |
      | "0.0.1"   | "0.0.360"           | "0.0.750"          |
      | "0.0.2"   | "0.0.361"           | "0.0.751"          |
      | "0.0.3"   | "0.0.362"           | "0.0.752"          |
      | "0.0.4"   | "0.0.363"           | "0.0.753"          |
      | "0.0.5"   | "0.0.364"           | "0.0.754"          |
      | "0.0.6"   | "0.0.365"           | "0.0.859"          |
      | "0.0.7"   | "0.0.366"           | "0.0.860"          |
      | "0.0.8"   | "0.0.367"           | "0.0.925"          |
      | "0.0.9"   | "0.0.368"           | "0.0.978"          |
      | "0.0.10"  | "0.0.500"           | "0.0.990"          |
      | "0.0.11"  | "0.0.555"           | "0.0.991"          |
      | "0.0.356" | "0.0.628"           | "0.0.992"          |
      | "0.0.357" | "0.0.629"           | "0.0.993"          |
      | "0.0.358" | "0.0.747"           | "0.0.997"          |
      | "0.0.359" | "0.0.748"           | "0.0.998"          |
      | "0.0.360" | "0.0.749"           | "0.0.999"          |
      | "0.0.361" | "0.0.750"           | "0.0.1000"         |
      | "0.0.750" | "0.0.751"           | "0.0.1001"         |

  Scenario Outline: Validate in-equivalence tests for system accounts with call, staticcall, delegatecall
  and callcode
    Given I successfully create equivalence call contract
    Then the mirror node REST API should return status 200 for the contracts creation
    Then I make internal <callType> to system account <account> <amountType> amount to <node> node

    Examples:
      | callType       | account   | amountType | node        |
      | "call"         | "0.0.0"   | "without"  | "MIRROR" |
      | "call"         | "0.0.0"   | "with"     | "MIRROR" |
      | "call"         | "0.0.9"   | "without"  | "MIRROR" |
      | "call"         | "0.0.9"   | "with"     | "MIRROR" |
      | "call"         | "0.0.357" | "without"  | "MIRROR" |
      | "call"         | "0.0.357" | "with"     | "MIRROR" |
      | "call"         | "0.0.358" | "without"  | "MIRROR" |
      | "call"         | "0.0.358" | "with"     | "MIRROR" |
      | "call"         | "0.0.741" | "without"  | "MIRROR" |
      | "call"         | "0.0.741" | "with"     | "MIRROR" |
      | "call"         | "0.0.800" | "without"  | "MIRROR" |
      | "call"         | "0.0.800" | "with"     | "MIRROR" |
      | "staticcall"   | "0.0.0"   | "without"  | "MIRROR" |
      | "staticcall"   | "0.0.0"   | "with"     | "MIRROR" |
      | "staticcall"   | "0.0.9"   | "without"  | "MIRROR" |
      | "staticcall"   | "0.0.9"   | "with"     | "MIRROR" |
      | "staticcall"   | "0.0.357" | "without"  | "MIRROR" |
      | "staticcall"   | "0.0.357" | "with"     | "MIRROR" |
      | "staticcall"   | "0.0.358" | "without"  | "MIRROR" |
      | "staticcall"   | "0.0.358" | "with"     | "MIRROR" |
      | "staticcall"   | "0.0.741" | "without"  | "MIRROR" |
      | "staticcall"   | "0.0.741" | "with"     | "MIRROR" |
      | "staticcall"   | "0.0.800" | "without"  | "MIRROR" |
      | "staticcall"   | "0.0.800" | "with"     | "MIRROR" |
      | "delegatecall" | "0.0.0"   | "without"  | "MIRROR" |
      | "delegatecall" | "0.0.0"   | "with"     | "MIRROR" |
      | "delegatecall" | "0.0.9"   | "without"  | "MIRROR" |
      | "delegatecall" | "0.0.9"   | "with"     | "MIRROR" |
      | "delegatecall" | "0.0.357" | "without"  | "MIRROR" |
      | "delegatecall" | "0.0.357" | "with"     | "MIRROR" |
      | "delegatecall" | "0.0.358" | "without"  | "MIRROR" |
      | "delegatecall" | "0.0.358" | "with"     | "MIRROR" |
      | "delegatecall" | "0.0.741" | "without"  | "MIRROR" |
      | "delegatecall" | "0.0.741" | "with"     | "MIRROR" |
      | "delegatecall" | "0.0.800" | "without"  | "MIRROR" |
      | "delegatecall" | "0.0.800" | "with"     | "MIRROR" |
      | "callcode"     | "0.0.0"   | "without"  | "MIRROR" |
      | "callcode"     | "0.0.0"   | "with"     | "MIRROR" |
      | "callcode"     | "0.0.9"   | "without"  | "MIRROR" |
      | "callcode"     | "0.0.9"   | "with"     | "MIRROR" |
      | "callcode"     | "0.0.357" | "without"  | "MIRROR" |
      | "callcode"     | "0.0.357" | "with"     | "MIRROR" |
      | "callcode"     | "0.0.358" | "without"  | "MIRROR" |
      | "callcode"     | "0.0.358" | "with"     | "MIRROR" |
      | "callcode"     | "0.0.741" | "without"  | "MIRROR" |
      | "callcode"     | "0.0.741" | "with"     | "MIRROR" |
      | "callcode"     | "0.0.800" | "without"  | "MIRROR" |
      | "callcode"     | "0.0.800" | "with"     | "MIRROR" |


  Scenario Outline: Validate in-equivalence tests for internal calls
    Given I successfully create equivalence call contract
    Given I successfully create estimate precompile contract
    Given I successfully create selfdestruct contract
    Given I ensure token "FUNGIBLE" has been created
    Given I ensure token "NFT" has been created
    Given I successfully create tokens
    And I associate "FUNGIBLE" to contract
    Then the mirror node REST API should return status 200 for the contracts creation
    Then I verify the equivalence contract bytecode is deployed
    Then I execute internal "call" against "payable" contract "with" amount to <node> node
    Then I execute internal "call" against "non-payable" contract "with" amount to <node> node
    Then I execute internal "call" against "payable" contract "without" amount to <node> node
    Then I execute internal "staticcall" against "payable" contract "without" amount to <node> node
    Then I execute internal "delegatecall" against "payable" contract "without" amount to <node> node
    Then I execute internal "callcode" against "payable" contract "with" amount to <node> node
    Then I execute internal "callcode" against "non-payable" contract "with" amount to <node> node
    Then I execute internal "callcode" against "payable" contract "without" amount to <node> node
    Then I execute internal "call" against Identity precompile to <node> node
    Then I execute internal "staticcall" against Identity precompile to <node> node
    Then I execute internal "delegatecall" against Identity precompile to <node> node
    Then I execute internal "call" against Ecrecover precompile to <node> node
    Then I execute internal "staticcall" against Ecrecover precompile to <node> node
    Then I execute internal "delegatecall" against Ecrecover precompile to <node> node
    Then I execute internal "call" against SHA-256 precompile to <node> node
    Then I execute internal "staticcall" against SHA-256 precompile to <node> node
    Then I execute internal "delegatecall" against SHA-256 precompile to <node> node
    Then I execute internal "call" against Ripemd-160 precompile to <node> node
    Then I execute internal "staticcall" against Ripemd-160 precompile to <node> node
    Then I execute internal "delegatecall" against Ripemd-160 precompile to <node> node
    Then I make internal "call" to ethereum precompile "0.0.1" address with amount to <node> node
    Then I make internal "call" to ethereum precompile "0.0.9" address with amount to <node> node
    Then I make internal "callcode" to ethereum precompile "0.0.1" address with amount to <node> node
    Then I make internal "callcode" to ethereum precompile "0.0.9" address with amount to <node> node
    Then I execute internal "call" against PRNG precompile address "without" amount to <node> node
    Then I execute internal "call" against PRNG precompile address "with" amount to <node> node
    Then I execute internal "staticcall" against PRNG precompile address "without" amount to <node> node
    Then I execute internal "delegatecall" against PRNG precompile address "without" amount to <node> node
    Then I execute internal "callcode" against PRNG precompile address "without" amount to <node> node
    Then I execute internal "callcode" against PRNG precompile address "with" amount to <node> node
    Then I execute internal "call" against exchange rate precompile address "without" amount to <node> node
    Then I execute internal "call" against exchange rate precompile address "with" amount to <node> node
    Then I execute internal "staticcall" against exchange rate precompile address "without" amount to <node> node
    Then I execute internal "delegatecall" against exchange rate precompile address "without" amount to <node> node
    Then I execute internal "call" against HTS precompile with isToken function for "FUNGIBLE" "without" amount to <node> node
    Then I execute internal "call" against HTS precompile with isToken function for "FUNGIBLE" "with" amount to <node> node
    Then I execute internal "staticcall" against HTS precompile with isToken function for "FUNGIBLE" "without" amount to <node> node
    Then I execute internal "delegatecall" against HTS precompile with isToken function for "FUNGIBLE" "without" amount to <node> node
#    Then I execute internal "callcode" against HTS precompile with isToken function for "FUNGIBLE" "without" amount to <node> node -> throws precompile_error
#    Then I execute internal "callcode" against HTS precompile with isToken function for "FUNGIBLE" "with" amount to <node> node -> throws precompile_error
    Then I make internal "call" to account "BOB" "with" amount from "OPERATOR"
    Then I make internal "call" to account "ALICE" "with" amount from "OPERATOR"
    Then I make internal "call" to account "ALICE" "with" amount from "OPERATOR"
    And I update the "BOB" account and token key for contract "EQUIVALENCE_CALL"
    Then I make internal "call" to account "ALICE" "with" amount from "BOB"

    Examples:
      | node        |
      | "CONSENSUS" |
      | "MIRROR"    |


  Scenario Outline: Validate in-equivalence tests for HTS Transfers
    Given I successfully create estimate precompile contract
    Then the mirror node REST API should return status 200 for the contracts creation
    Given I successfully create tokens
    Given I mint a new nft
    Then the mirror node REST API should return status 200 for the HAPI transactions
    Then I call precompile with transfer "FUNGIBLE" token to a <account> address
    Then I call precompile with transfer "NFT" token to a <account> address
    Then I call precompile with transferFrom "FUNGIBLE" token to a <account> address
    Then I call precompile with transferFrom "NFT" token to a <account> address

    Examples:
      | account     |
#      | "0.0.0" |  INVALID_ALIAS_KEY
      | "0.0.1"     |
      | "0.0.2"     |
      | "0.0.3"     |
      | "0.0.4"     |
      | "0.0.5"     |
      | "0.0.6"     |
      | "0.0.7"     |
      | "0.0.8"     |
      | "0.0.9"     |
      | "0.0.10"    |
      | "0.0.11"    |
#      | "0.0.350" |  INVALID_ALIAS_KEY
#      | "0.0.356" |  INVALID_ALIAS_KEY
#      | "0.0.357" |  INVALID_ALIAS_KEY
#      | "0.0.358" |  INVALID_ALIAS_KEY
#      | "0.0.359" |  INVALID_ALIAS_KEY
#      | "0.0.360" |  INVALID_ALIAS_KEY
#      | "0.0.361" |  INVALID_ALIAS_KEY
      | "0.0.750"   |
      | "0.0.800"   |
      | "0.0.10010" |

  Scenario: Validate in-equivalence tests for HTS Transfers with state modification
    Given I successfully create equivalence call contract
    Given I successfully create estimate precompile contract
    Then the mirror node REST API should return status 200 for the contracts creation
    Given I successfully create tokens
    Given I mint a new nft
    Then I call precompile with transferFromNFT to a "0.0.1" address
    Then I call precompile with transferFromNFT to a "0.0.9" address
    Then I call precompile with transferFromNFT to a "0.0.10" address
    Then I call precompile with transferFromNFT to a "0.0.11" address
#    Then I call precompile with transferFromNFT to a "0.0.350" address  INVALID_ALIAS_KEY
#    Then I call precompile with transferFromNFT to a "0.0.357" address  INVALID_ALIAS_KEY
#    Then I call precompile with transferFromNFT to a "0.0.358" address  INVALID_ALIAS_KEY
#    Then I call precompile with transferFromNFT to a "0.0.359" address  INVALID_ALIAS_KEY
#    Then I call precompile with transferFromNFT to a "0.0.360" address  INVALID_ALIAS_KEY
#    Then I call precompile with transferFromNFT to a "0.0.361" address  INVALID_ALIAS_KEY
    Then I call precompile with transferFromNFT to a "0.0.750" address
    Then I call precompile with transferFromNFT to a "0.0.800" address
    Then I call precompile with transferFrom "NFT" token to a contract
    Then I call precompile with transferFrom "FUNGIBLE" token to a contract
    Then I call precompile with transferFrom "FUNGIBLE" token to a "BOB" EVM address
#    Then I call precompile with transferFrom "NFT" token to a "BOB" EVM address //success, should fail
    Given I mint a new nft
    Then I call precompile with transferFrom "NFT" token to a "ALICE" EVM address
    Then I call precompile with transferFrom "FUNGIBLE" token to a "ALICE" EVM address
    Given I mint a new nft
    Then I call precompile with transferFrom "NFT" token to an EVM address
    Then I call precompile with transferFrom "FUNGIBLE" token to an EVM address
    Given I mint a new nft
    And I update the "BOB" account and token key for contract "ESTIMATE_PRECOMPILE"
    Then I call precompile with signer BOB to transferFrom "NFT" token to ALICE
    Then I call precompile with signer BOB to transferFrom "FUNGIBLE" token to ALICE