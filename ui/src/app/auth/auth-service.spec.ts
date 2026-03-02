import { AuthService, STORAGE_KEY_RETURN } from './auth-service';

describe('AuthService', () => {
  // @ts-ignore
  const authService = new AuthService(null, null, null);

  beforeEach(() => {
    localStorage.removeItem(STORAGE_KEY_RETURN);
  });

  it('should be created', () => {
    expect(authService).toBeTruthy();
  });

  it('storeReturn should not store /logout path', () => {
    authService.storeReturn('/logout');
    expect(localStorage.getItem(STORAGE_KEY_RETURN)).toBeNull();
  });

  it('storeReturn should not store /logout path with query params', () => {
    authService.storeReturn('/logout?timeout=true');
    expect(localStorage.getItem(STORAGE_KEY_RETURN)).toBeNull();
  });

  it('storeReturn should store valid return paths', () => {
    authService.storeReturn('/skills');
    expect(localStorage.getItem(STORAGE_KEY_RETURN)).toBe('/skills');
  });

  it('popReturn should return null for stored /logout path', () => {
    localStorage.setItem(STORAGE_KEY_RETURN, '/logout');
    expect(authService.popReturn()).toBeNull();
  });

  it('popReturn should return path for valid stored paths', () => {
    localStorage.setItem(STORAGE_KEY_RETURN, '/skills');
    expect(authService.popReturn()).toBe('/skills');
  });

  it('should return true with correct roles', () => {
    const requiredRoles: string[] = ['admin', 'curator'];
    const userRoles: string[] = ['curator', 'viewer'];
    expect(authService.hasRole(requiredRoles, userRoles)).toEqual(true);
  });

  it('should return false with incorrect roles', () => {
    const requiredRoles: string[] = ['admin', 'curator'];
    const userRoles: string[] = ['guest', 'viewer'];
    expect(authService.hasRole(requiredRoles, userRoles)).toEqual(false);
  });
});
