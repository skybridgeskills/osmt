import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { of } from 'rxjs';
import { CategoryLinksComponent } from './category-links.component';
import { CategoryService } from '../service/category.service';
import { AuthService } from '../../auth/auth-service';

describe('CategoryLinksComponent', () => {
  let component: CategoryLinksComponent;
  let fixture: ComponentFixture<CategoryLinksComponent>;
  let categoryService: jasmine.SpyObj<CategoryService>;
  let authService: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    const categoryServiceSpy = jasmine.createSpyObj('CategoryService', [
      'getAllPaginated',
    ]);
    categoryServiceSpy.getAllPaginated.and.returnValue(
      of({
        categories: [
          { id: 1, name: 'Category A', skillCount: 5 },
          { id: 2, name: 'Category B', skillCount: 3 },
        ],
        totalCount: 2,
      })
    );
    const authServiceSpy = jasmine.createSpyObj('AuthService', [
      'isAuthenticated',
    ]);
    authServiceSpy.isAuthenticated.and.returnValue(true);

    await TestBed.configureTestingModule({
      declarations: [CategoryLinksComponent],
      imports: [HttpClientTestingModule, RouterTestingModule],
      providers: [
        { provide: CategoryService, useValue: categoryServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();

    categoryService = TestBed.inject(
      CategoryService
    ) as jasmine.SpyObj<CategoryService>;
    authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    fixture = TestBed.createComponent(CategoryLinksComponent);
    component = fixture.componentInstance;
    component.categories = ['Category A', 'Category B'];
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load category ids when authenticated', () => {
    expect(categoryService.getAllPaginated).toHaveBeenCalledWith(
      1000,
      0,
      undefined
    );
  });

  it('should resolve category id by name', () => {
    expect(component.getId('Category A')).toBe(1);
    expect(component.getId('Category B')).toBe(2);
    expect(component.getId('Unknown')).toBeUndefined();
  });
});
