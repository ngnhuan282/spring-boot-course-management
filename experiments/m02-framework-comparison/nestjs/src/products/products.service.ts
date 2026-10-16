import {
  Injectable,
  NotFoundException,
} from '@nestjs/common';

import type { Product } from './product.model.js';
import { ProductRequestDto } from './dto/product-request.dto.js';

@Injectable()
export class ProductsService {
  private products: Product[] = [
    {
      id: 1,
      name: 'Laptop',
      price: 15000000,
    },
    {
      id: 2,
      name: 'Mouse',
      price: 300000,
    },
  ];

  private nextId = 3;

  getAll(): Product[] {
    return this.products;
  }

  getById(id: number): Product {
    const product = this.products.find(
      (p) => p.id === id,
    );

    if (!product) {
      throw new NotFoundException(
        `Product with id ${id} was not found`,
      );
    }

    return product;
  }

  create(request: ProductRequestDto): Product {
    const product: Product = {
      id: this.nextId++,
      name: request.name,
      price: request.price,
    };

    this.products.push(product);

    return product;
  }

  update(
    id: number,
    request: ProductRequestDto,
  ): Product {
    const oldProduct = this.getById(id);

    const updatedProduct: Product = {
      id: oldProduct.id,
      name: request.name,
      price: request.price,
    };

    const index = this.products.findIndex(
      (p) => p.id === id,
    );

    this.products[index] = updatedProduct;

    return updatedProduct;
  }

  delete(id: number): void {
    this.getById(id);

    this.products = this.products.filter(
      (p) => p.id !== id,
    );
  }
}