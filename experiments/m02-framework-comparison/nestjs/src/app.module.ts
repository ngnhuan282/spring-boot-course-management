import { Module } from '@nestjs/common';

import { HealthController } from './health/health.controller.js';
import { ProductsController } from './products/products.controller.js';
import { ProductsService } from './products/products.service.js';

@Module({
  imports: [],
  controllers: [
    HealthController,
    ProductsController,
  ],
  providers: [
    ProductsService,
  ],
})
export class AppModule {}

