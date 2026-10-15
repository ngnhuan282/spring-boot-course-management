import { IsNotEmpty, IsNumber, Min } from 'class-validator';

export class ProductRequestDto {
  @IsNotEmpty({
    message: 'Product name is required',
  })
  name!: string;

  @IsNumber()
  @Min(0, {
    message: 'Product price must be zero or positive',
  })
  price!: number;
}