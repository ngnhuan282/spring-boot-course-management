import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  ParseIntPipe,
  Post,
  Put,
} from '@nestjs/common';

import { ProductsService } from './products.service.js';
import { ProductRequestDto } from './dto/product-request.dto.js';

@Controller('api/products')
export class ProductsController {
  constructor(
    private readonly productsService: ProductsService,
  ) {}

  @Get()
  getAll() {
    return this.productsService.getAll();
  }

  @Get(':id')
  getById(
    @Param('id', ParseIntPipe) id: number,
  ) {
    return this.productsService.getById(id);
  }

  @Post()
  create(
    @Body() request: ProductRequestDto,
  ) {
    return this.productsService.create(request);
  }

  @Put(':id')
  update(
    @Param('id', ParseIntPipe) id: number,
    @Body() request: ProductRequestDto,
  ) {
    return this.productsService.update(
      id,
      request,
    );
  }

  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  delete(
    @Param('id', ParseIntPipe) id: number,
  ) {
    this.productsService.delete(id);
  }
}