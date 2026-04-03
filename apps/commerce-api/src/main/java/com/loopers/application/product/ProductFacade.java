package com.loopers.application.product;

import com.loopers.application.brand.BrandService;
import com.loopers.application.event.ProductViewedEvent;
import com.loopers.application.queue.ModeManager;
import com.loopers.application.stock.StockService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.stock.Stock;
import com.loopers.infrastructure.product.ProductCacheManager;
import com.loopers.infrastructure.product.ProductCacheManager.CachedPage;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.domain.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import static com.loopers.support.transaction.TransactionHelper.afterCommit;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final StockService stockService;
    private final ProductCacheManager productCacheManager;
    private final ApplicationEventPublisher eventPublisher;
    private final ModeManager modeManager;

    // Command

    @Transactional
    public ProductInfo register(ProductCommand.Register command) {
        Brand brand = brandService.getActiveBrand(command.brandId());
        Product product = productService.register(command);
        stockService.createStock(product.getId(), command.stockQuantity());
        ProductInfo info = ProductInfo.from(product, brand.getName(), command.stockQuantity());
        afterCommit(() -> productCacheManager.evictAllLists());
        return info;
    }

    @Transactional
    public ProductInfo updateInfo(Long productId, ProductCommand.UpdateInfo command) {
        if (command.stockQuantity() != null && (modeManager.isEvent() || modeManager.isDrain())) {
            throw new CoreException(ErrorType.LOCKED, "EVENT/DRAIN 모드에서는 재고를 수정할 수 없습니다");
        }
        Product product = productService.updateInfo(productId, command);
        if (command.stockQuantity() != null) {
            stockService.updateQuantity(productId, command.stockQuantity());
        }
        Brand brand = brandService.getBrand(product.getBrandId());
        Stock stock = stockService.getStock(productId);
        ProductInfo info = ProductInfo.from(product, brand.getName(), stock.getQuantity());
        afterCommit(() -> {
            productCacheManager.evictDetail(productId);
            productCacheManager.evictAllLists();
        });
        return info;
    }

    @Transactional
    public void delete(Long productId) {
        productService.delete(productId);
        afterCommit(() -> {
            productCacheManager.evictDetail(productId);
            productCacheManager.evictAllLists();
        });
    }

    // Query

    @Transactional(readOnly = true)
    public ProductInfo getDetail(Long productId) {
        Product product = productService.getProduct(productId);
        Brand brand = brandService.getBrand(product.getBrandId());
        Stock stock = stockService.getStock(productId);
        return ProductInfo.from(product, brand.getName(), stock.getQuantity());
    }

    @Transactional(readOnly = true)
    public ProductInfo getActiveDetail(Long productId) {
        Optional<ProductInfo> cached = productCacheManager.getDetail(productId);
        if (cached.isPresent()) {
            return cached.get();
        }

        Product product = productService.getActiveProduct(productId);
        Brand brand = brandService.getBrand(product.getBrandId());
        Stock stock = stockService.getStock(productId);
        ProductInfo info = ProductInfo.from(product, brand.getName(), stock.getQuantity());
        productCacheManager.putDetail(productId, info);
        eventPublisher.publishEvent(new ProductViewedEvent(null, productId));
        return info;
    }

    @Transactional(readOnly = true)
    public Page<ProductInfo> getActiveList(Long brandId, Pageable pageable) {
        String sort = pageable.getSort().toString();
        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();

        Optional<CachedPage> cached = productCacheManager.getList(brandId, sort, page, size);
        if (cached.isPresent()) {
            CachedPage cachedPage = cached.get();
            return new PageImpl<>(cachedPage.content(), PageRequest.of(cachedPage.page(), cachedPage.size()), cachedPage.totalElements());
        }

        Page<Product> products = productService.findActiveProducts(brandId, pageable);
        Page<ProductInfo> result = toProductInfoPage(products);
        productCacheManager.putList(brandId, sort, page, size,
                new CachedPage(result.getContent(), page, size, result.getTotalElements()));
        return result;
    }

    @Transactional(readOnly = true)
    public Page<ProductInfo> getList(String name, Long brandId, Boolean deleted, Pageable pageable) {
        Page<Product> products = productService.findProducts(name, brandId, deleted, pageable);
        return toProductInfoPage(products);
    }

    private Page<ProductInfo> toProductInfoPage(Page<Product> products) {
        Set<Long> brandIds = products.getContent().stream()
                .map(Product::getBrandId)
                .collect(Collectors.toSet());
        Set<Long> productIds = products.getContent().stream()
                .map(Product::getId)
                .collect(Collectors.toSet());

        Map<Long, Brand> brandMap = brandService.getBrandsMapByIds(brandIds);
        Map<Long, Stock> stockMap = productIds.isEmpty()
                ? Map.of()
                : stockService.getStocksMapByProductIds(productIds);

        for (Product product : products.getContent()) {
            if (!brandMap.containsKey(product.getBrandId())) {
                throw new CoreException(ErrorType.NOT_FOUND,
                        "브랜드 매핑 누락. productId=" + product.getId() + ", brandId=" + product.getBrandId());
            }
        }

        return products.map(product -> {
            Stock stock = stockMap.get(product.getId());
            int stockQuantity = stock != null ? stock.getQuantity() : 0;
            return ProductInfo.from(product, brandMap.get(product.getBrandId()).getName(), stockQuantity);
        });
    }
}
