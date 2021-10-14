package com.nftco.core.flow

import com.nftco.core.blockchain.MetadataField
import com.nftco.core.blockchain.Tenant
import com.nftco.core.flow.cadence.*
import com.nftco.flow.sdk.*
import com.nftco.flow.sdk.cadence.CadenceNamespace.Companion.ns
import com.nftco.flow.sdk.cadence.OptionalField
import com.nftco.flow.sdk.cadence.StringField
import com.nftco.flow.sdk.cadence.unmarshall
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import java.math.BigDecimal

@Configuration
class FlowTenantServiceConfig {

    @Bean
    fun flowTenantService(flow: FlowAccessApi): FlowTenantServiceImpl {
        return FlowTenantServiceImpl(flow = flow)
    }
}

interface FlowTenantService {

    fun deployTenantServiceContracts(
        params: FlowTransactionParams,
        adminNftCollectionPath: String,
        adminObjectPath: String,
        privateNftCollectionPath: String,
        publicNftCollectionPath: String,
        tenantId: String,
        tenantName: String,
        tenantDescription: String
    ): FlowId
    fun getTenantId(address: FlowAddress): String?
    fun getTenantServiceContractVersion(address: FlowAddress): UInt?
    fun transferFlowTokensToTenant(
        params: FlowTransactionParams,
        amount: BigDecimal,
        tenantAddress: FlowAddress
    ): FlowId
    fun getTenantFlowTokenBalance(tenantAddress: FlowAddress): BigDecimal
    fun closeTenant(params: FlowTransactionParams): FlowId

    fun createArchetype(
        params: FlowTransactionParams,
        name: String,
        description: String,
        metadata: Map<String, MetadataField>
    ): FlowId
    fun getArchetypeView(address: FlowAddress, id: ULong): ArchetypeView?
    fun closeArchetype(params: FlowTransactionParams, id: ULong): FlowId

    fun createArtifact(
        params: FlowTransactionParams,
        archetypeId: ULong,
        name: String,
        description: String,
        maxMintCount: ULong,
        metadata: Map<String, MetadataField>
    ): FlowId
    fun getArtifactView(address: FlowAddress, id: ULong): ArtifactView?
    fun closeArtifact(params: FlowTransactionParams, id: ULong): FlowId

    fun createSet(
        params: FlowTransactionParams,
        name: String,
        description: String,
        metadata: Map<String, MetadataField>
    ): FlowId
    fun getSetView(address: FlowAddress, id: ULong): SetView?
    fun closeSet(params: FlowTransactionParams, id: ULong): FlowId

    fun createPrint(
        params: FlowTransactionParams,
        artifactId: ULong,
        setId: ULong?,
        name: String,
        description: String,
        maxMintCount: ULong,
        metadata: Map<String, MetadataField>
    ): FlowId
    fun getPrintView(address: FlowAddress, id: ULong): PrintView?
    fun closePrint(params: FlowTransactionParams, id: ULong): FlowId

    fun createFaucet(
        params: FlowTransactionParams,
        artifactId: ULong,
        setId: ULong?,
        name: String,
        description: String,
        maxMintCount: ULong,
        metadata: Map<String, MetadataField>
    ): FlowId
    fun getFaucetView(address: FlowAddress, id: ULong): FaucetView?
    fun closeFaucet(params: FlowTransactionParams, id: ULong): FlowId

    fun mintNFT(
        params: FlowTransactionParams,
        artifactId: ULong,
        printId: ULong?,
        faucetId: ULong?,
        setId: ULong?,
        metadata: Map<String, MetadataField>
    ): FlowId
    fun batchMintNFTs(
        params: FlowTransactionParams,
        count: ULong,
        artifactId: ULong,
        printId: ULong?,
        faucetId: ULong?,
        setId: ULong?,
        metadata: Map<String, MetadataField>
    ): FlowId
    fun getNFTView(address: FlowAddress, id: ULong): NFTView?
    fun getNFTViews(address: FlowAddress, ids: Iterable<ULong>): Set<NFTView>
}

class FlowTenantServiceImpl(
    private val flow: FlowAccessApi
) : FlowTenantService {

    private val template: FlowTemplate = FlowTemplate(flow)

    // =========================
    // TENANT

    override fun deployTenantServiceContracts(
        params: FlowTransactionParams,
        adminNftCollectionPath: String,
        adminObjectPath: String,
        privateNftCollectionPath: String,
        publicNftCollectionPath: String,
        tenantId: String,
        tenantName: String,
        tenantDescription: String
    ): FlowId {

        val contractCode = ClassPathResource("cadence/TenantService.cdc")
            .inputStream
            .readAllBytes()
            .let { String(it) }

        val tx = flow.simpleFlowTransaction(
            address = params.address,
            signer = params.signer,
            keyIndex = params.keyIndex
        ) {
            script {
                """
                    transaction(contractName: String, code: String, tenantId: String, tenantName: String, tenantDescription: String) {
                        prepare(signer: AuthAccount) {
                            signer.contracts.add(
                                name: contractName,
                                code: code.utf8,
                                tenantId: tenantId,
                                tenantName: tenantName,
                                tenantDescription: tenantDescription,
                                ADMIN_NFT_COLLECTION_PATH: $adminNftCollectionPath,
                                ADMIN_OBJECT_PATH: $adminObjectPath,
                                PRIVATE_NFT_COLLECTION_PATH: $privateNftCollectionPath,
                                PUBLIC_NFT_COLLECTION_PATH: $publicNftCollectionPath
                            )
                        }
                    }
                """
            }
            gasLimit(params.gasLimit)
            arguments {
                arg { string(Tenant.NFT_CONTRACT_NAME) }
                arg { string(addressRegistry.processScript(contractCode)) }
                arg { string(tenantId) }
                arg { string(tenantName) }
                arg { string(tenantDescription) }
            }
        }.send()

        return tx.transactionId!!
    }

    override fun getTenantId(address: FlowAddress): String? {
        try {
            val result = flow.simpleFlowScript {
                script {
                    """
                        import TenantService from ${address.formatted}
                        pub fun main(address: Address): String {
                            return TenantService.getTenantId()
                        }
                    """.trimIndent()
                }
                arg { address(address.bytes) }
            }
            return (result.jsonCadence as StringField).value
        } catch (e: FlowException) {
            return null
        }
    }

    override fun getTenantServiceContractVersion(address: FlowAddress): UInt? {
        return template.getDeployedContractVersion(address, "TenantService")
    }

    override fun transferFlowTokensToTenant(params: FlowTransactionParams, amount: BigDecimal, tenantAddress: FlowAddress): FlowId {
        val tx = template.transferTokens(
            from = params,
            toAddress = tenantAddress,
            payer = params,
            tokenCoords = FungibleTokenCoordinates.FLOW_TOKEN,
            amount = amount
        ).send()
        return tx.transactionId!!
    }

    override fun getTenantFlowTokenBalance(tenantAddress: FlowAddress): BigDecimal {
        return template.getTokenBalance(tenantAddress, FungibleTokenCoordinates.FLOW_TOKEN)
    }

    override fun closeTenant(params: FlowTransactionParams): FlowId {
        val tx = flow.simpleFlowTransaction(address = params.address, signer = params.signer, keyIndex = params.keyIndex) {
            script {
                """
                    import TenantService from ${params.address.formatted}
                    transaction() {
                        prepare(signer: AuthAccount) {
                            let admin = signer.borrow<&TenantService.TenantAdmin>(from: TenantService.ADMIN_OBJECT_PATH)
                                ?? panic("Could not borrow TenantService.TenantAdmin reference")
                            admin.close()
                        }
                    }
                """.trimIndent()
            }
            gasLimit(params.gasLimit)
        }.send()
        return tx.transactionId!!
    }

    // =========================
    // ARCHETYPE

    override fun createArchetype(
        params: FlowTransactionParams,
        name: String,
        description: String,
        metadata: Map<String, MetadataField>
    ): FlowId {

        val tx = flow.simpleFlowTransaction(address = params.address, signer = params.signer, keyIndex = params.keyIndex) {
            script {
                """
                    import TenantService from ${params.address.formatted}
                    
                    transaction(name: String, description: String, metadata: {String: TenantService.MetadataField}) {
                        prepare(signer: AuthAccount) {

                            let admin = signer.borrow<&TenantService.TenantAdmin>(from: TenantService.ADMIN_OBJECT_PATH)
                                ?? panic("Could not borrow TenantService.TenantAdmin reference")
 
                            admin.createArchetype(
                                name: name,
                                description: description,
                                metadata: metadata
                             )
                        }
                    }
                """.trimIndent()
            }
            gasLimit(params.gasLimit)
            arguments {
                arg { string(name) }
                arg { string(description) }
                arg { metadata.marshall(ns(params.address, "TenantService")) }
            }
        }.send()
        return tx.transactionId!!
    }

    override fun getArchetypeView(address: FlowAddress, id: ULong): ArchetypeView? {
        val result = flow.simpleFlowScript {
            script {
                """
                    import TenantService from ${address.formatted}
                    pub fun main(id: UInt64): TenantService.ArchetypeView? {
                        return TenantService.getArchetypeView(id)
                    }
                """.trimIndent()
            }
            arg { uint64(id) }
        }

        return (result.jsonCadence as OptionalField).value?.let { Flow.unmarshall(ArchetypeView::class, it) }
    }

    override fun closeArchetype(params: FlowTransactionParams, id: ULong): FlowId {
        val tx = flow.simpleFlowTransaction(address = params.address, signer = params.signer, keyIndex = params.keyIndex) {
            script {
                """
                    import TenantService from ${params.address.formatted}
                    transaction(archetypeId: UInt64) {
                        prepare(signer: AuthAccount) {
                            let admin = signer.borrow<&TenantService.TenantAdmin>(from: TenantService.ADMIN_OBJECT_PATH)
                                ?? panic("Could not borrow TenantService.TenantAdmin reference")
                            admin.borrowArchetypeAdmin(archetypeId).close()
                        }
                    }
                """.trimIndent()
            }
            gasLimit(params.gasLimit)
            arguments {
                arg { uint64(id) }
            }
        }.send()
        return tx.transactionId!!
    }

    // =========================
    // ARTIFACT

    override fun createArtifact(
        params: FlowTransactionParams,
        archetypeId: ULong,
        name: String,
        description: String,
        maxMintCount: ULong,
        metadata: Map<String, MetadataField>
    ): FlowId {

        val tx = flow.simpleFlowTransaction(address = params.address, signer = params.signer, keyIndex = params.keyIndex) {
            script {
                """
                    import TenantService from ${params.address.formatted}
                    
                    transaction(archetypeId: UInt64, name: String, description: String, maxMintCount: UInt64, metadata: {String: TenantService.MetadataField}) {
                        prepare(signer: AuthAccount) {

                            let admin = signer.borrow<&TenantService.TenantAdmin>(from: TenantService.ADMIN_OBJECT_PATH)
                                ?? panic("Could not borrow TenantService.TenantAdmin reference")
 
                            admin.createArtifact(
                                archetypeId: archetypeId,
                                name: name,
                                description: description,
                                maxMintCount: maxMintCount,
                                metadata: metadata
                             )
                        }
                    }
                """.trimIndent()
            }
            gasLimit(params.gasLimit)
            arguments {
                arg { uint64(archetypeId) }
                arg { string(name) }
                arg { string(description) }
                arg { uint64(maxMintCount) }
                arg { metadata.marshall(ns(params.address, "TenantService")) }
            }
        }.send()
        return tx.transactionId!!
    }

    override fun getArtifactView(address: FlowAddress, id: ULong): ArtifactView? {
        val result = flow.simpleFlowScript {
            script {
                """
                    import TenantService from ${address.formatted}
                    pub fun main(id: UInt64): TenantService.ArtifactView? {
                        return TenantService.getArtifactView(id)
                    }
                """.trimIndent()
            }
            arg { uint64(id) }
        }

        return (result.jsonCadence as OptionalField).value?.let { Flow.unmarshall(ArtifactView::class, it) }
    }

    override fun closeArtifact(params: FlowTransactionParams, id: ULong): FlowId {
        val tx = flow.simpleFlowTransaction(address = params.address, signer = params.signer, keyIndex = params.keyIndex) {
            script {
                """
                    import TenantService from ${params.address.formatted}
                    transaction(artifactId: UInt64) {
                        prepare(signer: AuthAccount) {
                            let admin = signer.borrow<&TenantService.TenantAdmin>(from: TenantService.ADMIN_OBJECT_PATH)
                                ?? panic("Could not borrow TenantService.TenantAdmin reference")
                            admin.borrowArtifactAdmin(artifactId).close()
                        }
                    }
                """.trimIndent()
            }
            arguments {
                arg { uint64(id) }
            }
            gasLimit(params.gasLimit)
        }.send()
        return tx.transactionId!!
    }

    // =========================
    // SET

    override fun createSet(
        params: FlowTransactionParams,
        name: String,
        description: String,
        metadata: Map<String, MetadataField>
    ): FlowId {

        val tx = flow.simpleFlowTransaction(address = params.address, signer = params.signer, keyIndex = params.keyIndex) {
            script {
                """
                    import TenantService from ${params.address.formatted}
                    
                    transaction(name: String, description: String, metadata: {String: TenantService.MetadataField}) {
                        prepare(signer: AuthAccount) {

                            let admin = signer.borrow<&TenantService.TenantAdmin>(from: TenantService.ADMIN_OBJECT_PATH)
                                ?? panic("Could not borrow TenantService.TenantAdmin reference")
 
                            admin.createSet(
                                name: name,
                                description: description,
                                metadata: metadata
                             )
                        }
                    }
                """.trimIndent()
            }
            gasLimit(params.gasLimit)
            arguments {
                arg { string(name) }
                arg { string(description) }
                arg { metadata.marshall(ns(params.address, "TenantService")) }
            }
        }.send()
        return tx.transactionId!!
    }

    override fun getSetView(address: FlowAddress, id: ULong): SetView? {
        val result = flow.simpleFlowScript {
            script {
                """
                    import TenantService from ${address.formatted}
                    pub fun main(id: UInt64): TenantService.SetView? {
                        return TenantService.getSetView(id)
                    }
                """.trimIndent()
            }
            arg { uint64(id) }
        }
        return (result.jsonCadence as OptionalField).value?.let { Flow.unmarshall(SetView::class, it) }
    }

    override fun closeSet(params: FlowTransactionParams, id: ULong): FlowId {
        val tx = flow.simpleFlowTransaction(address = params.address, signer = params.signer, keyIndex = params.keyIndex) {
            script {
                """
                    import TenantService from ${params.address.formatted}
                    transaction(id: UInt64) {
                        prepare(signer: AuthAccount) {
                            let admin = signer.borrow<&TenantService.TenantAdmin>(from: TenantService.ADMIN_OBJECT_PATH)
                                ?? panic("Could not borrow TenantService.TenantAdmin reference")
                            admin.borrowSetAdmin(id).close()
                        }
                    }
                """.trimIndent()
            }
            gasLimit(params.gasLimit)
            arguments {
                arg { uint64(id) }
            }
        }.send()
        return tx.transactionId!!
    }

    // =========================
    // PRINT

    override fun createPrint(
        params: FlowTransactionParams,
        artifactId: ULong,
        setId: ULong?,
        name: String,
        description: String,
        maxMintCount: ULong,
        metadata: Map<String, MetadataField>
    ): FlowId {

        val tx = flow.simpleFlowTransaction(address = params.address, signer = params.signer, keyIndex = params.keyIndex) {
            script {
                """
                    import TenantService from ${params.address.formatted}
                    
                    transaction(
                        artifactId: UInt64, 
                        setId: UInt64?, 
                        name: String, 
                        description: String, 
                        maxMintCount: UInt64,
                        metadata: {String: TenantService.MetadataField}
                    ) {
                        prepare(signer: AuthAccount) {

                            let admin = signer.borrow<&TenantService.TenantAdmin>(from: TenantService.ADMIN_OBJECT_PATH)
                                ?? panic("Could not borrow TenantService.TenantAdmin reference")
 
                            admin.createPrint(
                                artifactId: artifactId,
                                setId: setId,
                                name: name,
                                description: description,
                                maxMintCount: maxMintCount,
                                metadata: metadata
                             )
                        }
                    }
                """.trimIndent()
            }
            gasLimit(params.gasLimit)
            arguments {
                arg { uint64(artifactId) }
                arg { optional(setId) { uint64(it) } }
                arg { string(name) }
                arg { string(description) }
                arg { uint64(maxMintCount) }
                arg { metadata.marshall(ns(params.address, "TenantService")) }
            }
        }.send()
        return tx.transactionId!!
    }

    override fun getPrintView(address: FlowAddress, id: ULong): PrintView? {
        val result = flow.simpleFlowScript {
            script {
                """
                    import TenantService from ${address.formatted}
                    pub fun main(id: UInt64): TenantService.PrintView? {
                        return TenantService.getPrintView(id)
                    }
                """.trimIndent()
            }
            arg { uint64(id) }
        }
        return (result.jsonCadence as OptionalField).value?.let { Flow.unmarshall(PrintView::class, it) }
    }

    override fun closePrint(params: FlowTransactionParams, id: ULong): FlowId {
        val tx = flow.simpleFlowTransaction(address = params.address, signer = params.signer, keyIndex = params.keyIndex) {
            script {
                """
                    import TenantService from ${params.address.formatted}
                    transaction(id: UInt64) {
                        prepare(signer: AuthAccount) {
                            let admin = signer.borrow<&TenantService.TenantAdmin>(from: TenantService.ADMIN_OBJECT_PATH)
                                ?? panic("Could not borrow TenantService.TenantAdmin reference")
                            admin.borrowPrintAdmin(id).close()
                        }
                    }
                """.trimIndent()
            }
            gasLimit(params.gasLimit)
            arguments {
                arg { uint64(id) }
            }
        }.send()
        return tx.transactionId!!
    }

    // =========================
    // FAUCET

    override fun createFaucet(
        params: FlowTransactionParams,
        artifactId: ULong,
        setId: ULong?,
        name: String,
        description: String,
        maxMintCount: ULong,
        metadata: Map<String, MetadataField>
    ): FlowId {

        val tx = flow.simpleFlowTransaction(address = params.address, signer = params.signer, keyIndex = params.keyIndex) {
            script {
                """
                    import TenantService from ${params.address.formatted}
                    
                    transaction(
                        artifactId: UInt64, 
                        setId: UInt64?, 
                        name: String, 
                        description: String, 
                        maxMintCount: UInt64,
                        metadata: {String: TenantService.MetadataField}
                    ) {
                        prepare(signer: AuthAccount) {

                            let admin = signer.borrow<&TenantService.TenantAdmin>(from: TenantService.ADMIN_OBJECT_PATH)
                                ?? panic("Could not borrow TenantService.TenantAdmin reference")
 
                            admin.createFaucet(
                                artifactId: artifactId,
                                setId: setId,
                                name: name,
                                description: description,
                                maxMintCount: maxMintCount,
                                metadata: metadata
                             )
                        }
                    }
                """.trimIndent()
            }
            gasLimit(params.gasLimit)
            arguments {
                arg { uint64(artifactId) }
                arg { optional(setId) { uint64(it) } }
                arg { string(name) }
                arg { string(description) }
                arg { uint64(maxMintCount) }
                arg { metadata.marshall(ns(params.address, "TenantService")) }
            }
        }.send()
        return tx.transactionId!!
    }

    override fun getFaucetView(address: FlowAddress, id: ULong): FaucetView? {
        val result = flow.simpleFlowScript {
            script {
                """
                    import TenantService from ${address.formatted}
                    pub fun main(id: UInt64): TenantService.FaucetView? {
                        return TenantService.getFaucetView(id)
                    }
                """.trimIndent()
            }
            arg { uint64(id) }
        }
        return (result.jsonCadence as OptionalField).value?.let { Flow.unmarshall(FaucetView::class, it) }
    }

    override fun closeFaucet(params: FlowTransactionParams, id: ULong): FlowId {
        val tx = flow.simpleFlowTransaction(address = params.address, signer = params.signer, keyIndex = params.keyIndex) {
            script {
                """
                    import TenantService from ${params.address.formatted}
                    transaction(id: UInt64) {
                        prepare(signer: AuthAccount) {
                            let admin = signer.borrow<&TenantService.TenantAdmin>(from: TenantService.ADMIN_OBJECT_PATH)
                                ?? panic("Could not borrow TenantService.TenantAdmin reference")
                            admin.borrowFaucetAdmin(id).close()
                        }
                    }
                """.trimIndent()
            }
            gasLimit(params.gasLimit)
            arguments {
                arg { uint64(id) }
            }
        }.send()
        return tx.transactionId!!
    }

    override fun mintNFT(
        params: FlowTransactionParams,
        artifactId: ULong,
        printId: ULong?,
        faucetId: ULong?,
        setId: ULong?,
        metadata: Map<String, MetadataField>
    ): FlowId {
        val tx = flow.simpleFlowTransaction(address = params.address, signer = params.signer, keyIndex = params.keyIndex) {
            script {
                """
                    import TenantService from ${params.address.formatted}
                    
                    transaction(
                        artifactId: UInt64, 
                        printId: UInt64?,
                        faucetId: UInt64?,
                        setId: UInt64?, 
                        metadata: {String: TenantService.MetadataField}
                    ) {
                        prepare(signer: AuthAccount) {

                            let admin = signer.borrow<&TenantService.TenantAdmin>(from: TenantService.ADMIN_OBJECT_PATH)
                                ?? panic("Could not borrow TenantService.TenantAdmin reference")

                            let collection = signer.borrow<&TenantService.ShardedCollection>(from: TenantService.ADMIN_NFT_COLLECTION_PATH)
                                ?? panic("Could not borrow TenantService.TenantNFTCollection reference")
 
                            let nft <- admin.mintNFT(
                                artifactId: artifactId,
                                printId: printId,
                                faucetId: faucetId,
                                setId: setId,
                                metadata: metadata
                             )
                             
                             collection.deposit(token: <- nft)
                        }
                    }
                """.trimIndent()
            }
            gasLimit(params.gasLimit)
            arguments {
                arg { uint64(artifactId) }
                arg { optional(printId) { uint64(it) } }
                arg { optional(faucetId) { uint64(it) } }
                arg { optional(setId) { uint64(it) } }
                arg { metadata.marshall(ns(params.address, "TenantService")) }
            }
        }.send()
        return tx.transactionId!!
    }

    override fun batchMintNFTs(
        params: FlowTransactionParams,
        count: ULong,
        artifactId: ULong,
        printId: ULong?,
        faucetId: ULong?,
        setId: ULong?,
        metadata: Map<String, MetadataField>
    ): FlowId {
        val tx = flow.simpleFlowTransaction(address = params.address, signer = params.signer, keyIndex = params.keyIndex) {
            script {
                """
                    import TenantService from ${params.address.formatted}
                    
                    transaction(
                        count: UInt64,
                        artifactId: UInt64, 
                        printId: UInt64?,
                        faucetId: UInt64?,
                        setId: UInt64?, 
                        metadata: {String: TenantService.MetadataField}
                    ) {
                        prepare(signer: AuthAccount) {

                            let admin = signer.borrow<&TenantService.TenantAdmin>(from: TenantService.ADMIN_OBJECT_PATH)
                                ?? panic("Could not borrow TenantService.TenantAdmin reference")
 
                            let collection = signer.borrow<&TenantService.ShardedCollection>(from: TenantService.ADMIN_NFT_COLLECTION_PATH)
                                ?? panic("Could not borrow TenantService.TenantNFTCollection reference")
 
                            let nfts <- admin.batchMintNFTs(
                                count: count,
                                artifactId: artifactId,
                                printId: printId,
                                faucetId: faucetId,
                                setId: setId,
                                metadata: metadata
                             )
                             
                             collection.batchDeposit(tokens: <- nfts)
                        }
                    }
                """.trimIndent()
            }
            gasLimit(params.gasLimit)
            arguments {
                arg { uint64(count) }
                arg { uint64(artifactId) }
                arg { optional(printId) { uint64(it) } }
                arg { optional(faucetId) { uint64(it) } }
                arg { optional(setId) { uint64(it) } }
                arg { metadata.marshall(ns(params.address, "TenantService")) }
            }
        }.send()
        return tx.transactionId!!
    }

    override fun getNFTView(address: FlowAddress, id: ULong): NFTView? {
        val result = flow.simpleFlowScript {
            script {
                """
                    import TenantService from ${address.formatted}
                    pub fun main(id: UInt64, ownerAddress: Address): TenantService.NFTView? {
                    
                        let ownerAccount = getAccount(ownerAddress)
                            
                        let publicCollection = ownerAccount.getCapability(TenantService.PUBLIC_NFT_COLLECTION_PATH)
                            .borrow<&{TenantService.CollectionPublic}>()
                            ?? panic("Could not find nft collection for that account")
                            
                        let nft = publicCollection.borrowNFTData(id: id)
                            ?? panic("Could not find NFT")
                        
                        return TenantService.getNFTView(nft)
                    }
                """.trimIndent()
            }
            arg { uint64(id) }
            arg { address(address.formatted) }
        }
        return (result.jsonCadence as OptionalField).value?.let { Flow.unmarshall(NFTView::class, it) }
    }

    override fun getNFTViews(address: FlowAddress, ids: Iterable<ULong>): Set<NFTView> {
        val result = flow.simpleFlowScript {
            script {
                """
                    import TenantService from ${address.formatted}
                    pub fun main(ids: [UInt64], ownerAddress: Address): [TenantService.NFTView] {
                    
                        let ownerAccount = getAccount(ownerAddress)
                            
                        let publicCollection = ownerAccount.getCapability(TenantService.PUBLIC_NFT_COLLECTION_PATH)
                            .borrow<&{TenantService.CollectionPublic}>()
                            ?? panic("Could not find nft collection for that account")
                            
                        let nfts = publicCollection.borrowNFTDatas(ids: ids)
                        
                        return TenantService.getNFTViews(nfts)
                    }
                """.trimIndent()
            }
            arg { array(ids) { uint64(it) } }
            arg { address(address.formatted) }
        }
        return unmarshall(result.jsonCadence) {
            arrayValues(result.jsonCadence) {
                Flow.unmarshall(NFTView::class, it)
            }
        }.toSet()
    }
}
