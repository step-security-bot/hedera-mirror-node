// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;


contract ContractWithConstructor {
    address public owner;

    constructor() {
        owner = msg.sender;
    }
}

contract EmptyContract {
    address public owner;
}

contract Deploys1Contract {
    address public owner;
    EmptyContract  public emptyContract;

    constructor() {
        owner = msg.sender;
        // Deploy Chain within ChildContract constructor
        //  chainContract = new ChainContract();
        EmptyContract childContract = new EmptyContract();
    }
}

contract Deploys2Contracts {
    address public owner;
    Deploys1Contract  public childContract;

    constructor() {
        owner = msg.sender;
        // Deploy Chain within ChildContract constructor
        //  chainContract = new ChainContract();
        Deploys1Contract childContract = new Deploys1Contract();
    }
}

contract Deploys3Contracts {
    address public owner;
    Deploys2Contracts  public childContract;

    constructor() {
        owner = msg.sender;
        // Deploy Chain within ChildContract constructor
        //  chainContract = new ChainContract();
        Deploys2Contracts childContract = new Deploys2Contracts();
    }
}

contract Deploys10Contracts {
    address public owner;
    Deploys2Contracts  public childContract;

    constructor() {
        owner = msg.sender;
        for (uint i = 1; i < 20; i++) {
            EmptyContract childContract = new EmptyContract();
        }
    }
}

contract Deploys13NestedContracts {
    address public owner;

    constructor() {
        owner = msg.sender;
        Deploys10Contracts child1Contract = new Deploys10Contracts();
        Deploys3Contracts child2Contract = new Deploys3Contracts();
    }
}

contract Deploys33NestedContracts {
    address public owner;

    constructor() {
        owner = msg.sender;
        Deploys10Contracts child1Contract = new Deploys10Contracts();
        Deploys10Contracts child2Contract = new Deploys10Contracts();
        // Deploys10Contracts child3Contract = new Deploys10Contracts();
        Deploys3Contracts child4Contract = new Deploys3Contracts();
    }
}


