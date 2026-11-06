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
 * Корзины покупателей. У каждого покупателя своя корзина: все операции принимают id покупателя
 * и работают только с его позициями. В базе хранятся только id товаров и количество, данные товаров
 * берутся из каталога {@link ItemService}, то есть из кеша Redis.
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
    public Mono<Void> changeQuantity(long userId, long itemId, CartAction action) {
        return switch (action) {
            case PLUS -> increase(userId, itemId);
            case MINUS -> decrease(userId, itemId);
            case DELETE -> cartItemRepository.findByUserIdAndItemId(userId, itemId).flatMap(cartItemRepository::delete);
        };
    }

    public Mono<CartDto> getCart(long userId) {
        return getCartLines(userId)
                .collectList()
                .map(CartService::toCart);
    }

    public Flux<CartLine> getCartLines(long userId) {
        return cartItemRepository.findByUserIdOrderByIdAsc(userId)
                .flatMapSequential(cartItem -> itemService.getItem(cartItem.getItemId())
                        .map(card -> ItemMapper.toCartLine(card, cartItem.getQuantity())));
    }

    public Mono<Integer> getCount(long userId, long itemId) {
        return cartItemRepository.findByUserIdAndItemId(userId, itemId)
                .map(CartItem::getQuantity)
                .defaultIfEmpty(0);
    }

    public Mono<Map<Long, Integer>> getCounts(long userId, Collection<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return Mono.just(Map.of());
        }
        return cartItemRepository.findByUserIdAndItemIdIn(userId, itemIds)
                .collectMap(CartItem::getItemId, CartItem::getQuantity);
    }

    @Transactional
    public Mono<Void> clear(long userId) {
        return cartItemRepository.deleteByUserId(userId);
    }

    private Mono<Void> increase(long userId, long itemId) {
        return cartItemRepository.findByUserIdAndItemId(userId, itemId)
                .doOnNext(CartItem::increment)
                .switchIfEmpty(Mono.defer(() -> itemService.getItem(itemId).map(card -> new CartItem(userId, itemId))))
                .flatMap(cartItemRepository::save)
                .then();
    }

    private Mono<Void> decrease(long userId, long itemId) {
        return cartItemRepository.findByUserIdAndItemId(userId, itemId)
                .flatMap(cartItem -> cartItem.decrement()
                        ? cartItemRepository.save(cartItem).then()
                        : cartItemRepository.delete(cartItem));
    }

    private static CartDto toCart(List<CartLine> cartLines) {
        return new CartDto(cartLines.stream().map(ItemMapper::toDto).toList(), CartLine.total(cartLines));
    }
}
