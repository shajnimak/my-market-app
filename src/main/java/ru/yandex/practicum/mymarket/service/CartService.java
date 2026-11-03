package ru.yandex.practicum.mymarket.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.mymarket.dto.CartDto;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.exception.ItemNotFoundException;
import ru.yandex.practicum.mymarket.mapper.ItemMapper;
import ru.yandex.practicum.mymarket.model.CartAction;
import ru.yandex.practicum.mymarket.model.CartItem;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ItemRepository itemRepository;

    public CartService(CartItemRepository cartItemRepository, ItemRepository itemRepository) {
        this.cartItemRepository = cartItemRepository;
        this.itemRepository = itemRepository;
    }

    @Transactional
    public void changeQuantity(long itemId, CartAction action) {
        switch (action) {
            case PLUS -> increase(itemId);
            case MINUS -> decrease(itemId);
            case DELETE -> cartItemRepository.findByItemId(itemId).ifPresent(cartItemRepository::delete);
        }
    }

    public CartDto getCart() {
        List<CartItem> cartItems = getCartItems();
        List<ItemDto> items = cartItems.stream()
                .map(cartItem -> ItemMapper.toDto(cartItem.getItem(), cartItem.getQuantity()))
                .toList();
        long total = cartItems.stream().mapToLong(CartItem::getTotalPrice).sum();
        return new CartDto(items, total);
    }

    public List<CartItem> getCartItems() {
        return cartItemRepository.findAllByOrderByIdAsc();
    }

    public int getCount(long itemId) {
        return cartItemRepository.findByItemId(itemId)
                .map(CartItem::getQuantity)
                .orElse(0);
    }

    public Map<Long, Integer> getCounts(Collection<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return cartItemRepository.findByItemIdIn(itemIds).stream()
                .collect(Collectors.toMap(cartItem -> cartItem.getItem().getId(), CartItem::getQuantity));
    }

    @Transactional
    public void clear() {
        cartItemRepository.deleteAllInBatch();
    }

    private void increase(long itemId) {
        cartItemRepository.findByItemId(itemId).ifPresentOrElse(
                CartItem::increment,
                () -> {
                    Item item = itemRepository.findById(itemId)
                            .orElseThrow(() -> new ItemNotFoundException(itemId));
                    cartItemRepository.save(new CartItem(item));
                }
        );
    }

    private void decrease(long itemId) {
        cartItemRepository.findByItemId(itemId)
                .filter(cartItem -> !cartItem.decrement())
                .ifPresent(cartItemRepository::delete);
    }
}
