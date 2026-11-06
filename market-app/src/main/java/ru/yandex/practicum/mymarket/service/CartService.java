package ru.yandex.practicum.mymarket.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartDto;
import ru.yandex.practicum.mymarket.mapper.ItemMapper;
import ru.yandex.practicum.mymarket.model.CartAction;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.CartLine;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Корзина. В базе хранятся только id товаров и количество, данные товаров берутся
 * из каталога {@link ItemService}, то есть из кеша Redis.
 */
@Service
@Transactional(readOnly = true)
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ItemService itemService;

    public CartService(CartItemRepository cartItemRepository, ItemService itemService) {
        this.cartItemRepository = cartItemRepository;
        this.itemService = itemService;
    }

    @Transactional
    public Mono<Void> changeQuantity(long itemId, CartAction action) {
        return switch (action) {
            case PLUS -> increase(itemId);
            case MINUS -> decrease(itemId);
            case DELETE -> cartItemRepository.findByItemId(itemId).flatMap(cartItemRepository::delete);
        };
    }

    public Mono<CartDto> getCart() {
        return getCartLines()
                .collectList()
                .map(CartService::toCart);
    }

    public Flux<CartLine> getCartLines() {
        return cartItemRepository.findAllByOrderByIdAsc()
                .flatMapSequential(cartItem -> itemService.getItem(cartItem.getItemId())
                        .map(card -> ItemMapper.toCartLine(card, cartItem.getQuantity())));
    }

    public Mono<Integer> getCount(long itemId) {
        return cartItemRepository.findByItemId(itemId)
                .map(CartItem::getQuantity)
                .defaultIfEmpty(0);
    }

    public Mono<Map<Long, Integer>> getCounts(Collection<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return Mono.just(Map.of());
        }
        return cartItemRepository.findByItemIdIn(itemIds)
                .collectMap(CartItem::getItemId, CartItem::getQuantity);
    }

    @Transactional
    public Mono<Void> clear() {
        return cartItemRepository.deleteAll();
    }

    private Mono<Void> increase(long itemId) {
        return cartItemRepository.findByItemId(itemId)
                .doOnNext(CartItem::increment)
                .switchIfEmpty(Mono.defer(() -> itemService.getItem(itemId).map(card -> new CartItem(itemId))))
                .flatMap(cartItemRepository::save)
                .then();
    }

    private Mono<Void> decrease(long itemId) {
        return cartItemRepository.findByItemId(itemId)
                .flatMap(cartItem -> cartItem.decrement()
                        ? cartItemRepository.save(cartItem).then()
                        : cartItemRepository.delete(cartItem));
    }

    private static CartDto toCart(List<CartLine> cartLines) {
        return new CartDto(cartLines.stream().map(ItemMapper::toDto).toList(), CartLine.total(cartLines));
    }
}
